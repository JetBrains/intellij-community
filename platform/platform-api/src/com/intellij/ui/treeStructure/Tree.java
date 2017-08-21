/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.awt.event.*;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

public class Tree extends JTree implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer>, Autoscroll, Queryable,
                                           ComponentWithFileColors {
  private final StatusText myEmptyText;
  private final ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;
  private Rectangle myLastVisibleRec;

  private Dimension myHoldSize;
  private final MySelectionModel mySelectionModel = new MySelectionModel();
  private boolean myHorizontalAutoScrolling = true;

  private TreePath rollOverPath;

  public Tree() {
    this(getDefaultTreeModel());
  }

  public Tree(TreeNode root) {
    this(new DefaultTreeModel(root, false));
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return Tree.this.isEmpty();
      }
    };

    myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);

    if (UIUtil.isUnderWin10LookAndFeel()) {
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          Point p = e.getPoint();
          TreePath newPath = getPathForLocation(p.x, p.y);
          if (newPath != null && !newPath.equals(rollOverPath)) {
            TreeCellRenderer renderer = getCellRenderer();
            if (newPath.getLastPathComponent() instanceof TreeNode) {
              TreeNode node = (TreeNode)newPath.getLastPathComponent();
              JComponent c = (JComponent)renderer.getTreeCellRendererComponent(
                Tree.this, node,
                isPathSelected(newPath),
                isExpanded(newPath),
                getModel().isLeaf(node),
                getRowForPath(newPath), hasFocus());

              c.putClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY, c instanceof JCheckBox ? getPathBounds(newPath) : node);
              rollOverPath = newPath;
              UIUtil.repaintViewport(Tree.this);
            }
          }
        }
      });
    }

    addMouseListener(new MyMouseListener());
    addFocusListener(new MyFocusListener());

    setCellRenderer(new NodeRenderer());

    setSelectionModel(mySelectionModel);
    setOpaque(false);
  }

  @Override
  public void setUI(TreeUI ui) {
    TreeUI actualUI = ui;
    if (!isCustomUI()) {
      if (!(ui instanceof WideSelectionTreeUI) && isWideSelection() && !UIUtil.isUnderGTKLookAndFeel()) {
        actualUI = new WideSelectionTreeUI(isWideSelection(), getWideSelectionBackgroundCondition());
      }
    }
    super.setUI(actualUI);
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  public boolean isEmpty() {
    TreeModel model = getModel();
    if (model == null) return true;
    if (model.getRoot() == null) return true;
    if (!isRootVisible()) {
      int childCount = model.getChildCount(model.getRoot());
      if (childCount == 0) {
        return true;
      }
      if (childCount == 1) {
        Object node = model.getChild(model.getRoot(), 0);
        if (node instanceof LoadingNode) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean isCustomUI() {
    return false;
  }

  protected boolean isWideSelection() {
    return true;
  }

  /**
   * @return a strategy which determines if a wide selection should be drawn for a target row (it's number is
   * {@link Condition#value(Object) given} as an argument to the strategy)
   */
  @SuppressWarnings("unchecked")
  @NotNull
  protected Condition<Integer> getWideSelectionBackgroundCondition() {
    return Conditions.alwaysTrue();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return false;
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  @NotNull
  public ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  @Override
  public void setExpandableItemsEnabled(boolean enabled) {
    myExpandableItemsHandler.setEnabled(enabled);
  }

  @Override
  public Color getBackground() {
    return isBackgroundSet() ? super.getBackground() : UIUtil.getTreeTextBackground();
  }

  @Override
  public Color getForeground() {
    return isForegroundSet() ? super.getForeground() : UIUtil.getTreeForeground();
  }

  @Override
  public void addNotify() {
    super.addNotify();

    updateBusy();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (myBusyIcon != null) {
      remove(myBusyIcon);
      Disposer.dispose(myBusyIcon);
      myBusyIcon = null;
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();

    updateBusyIconLocation();
  }

  private void updateBusyIconLocation() {
    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  @Override
  public void paint(Graphics g) {
    Rectangle visible = getVisibleRect();

    boolean canHoldSelection = false;
    TreePath[] paths = getSelectionModel().getSelectionPaths();
    if (paths != null) {
      for (TreePath each : paths) {
        Rectangle selection = getPathBounds(each);
        if (selection != null && (g.getClipBounds().intersects(selection) || g.getClipBounds().contains(selection))) {
          if (myBusy && myBusyIcon != null) {
            Rectangle busyIconBounds = myBusyIcon.getBounds();
            if (selection.contains(busyIconBounds) || selection.intersects(busyIconBounds)) {
              canHoldSelection = false;
              break;
            }
            else {
              canHoldSelection = true;
            }
          }
          else {
            canHoldSelection = true;
          }
        }
      }
    }

    if (canHoldSelection) {
      if (!AbstractTreeBuilder.isToPaintSelection(this)) {
        mySelectionModel.holdSelection();
      }
    }

    try {
      super.paint(g);

      if (!visible.equals(myLastVisibleRec)) {
        updateBusyIconLocation();
      }

      myLastVisibleRec = visible;
    }
    finally {
      mySelectionModel.unholdSelection();
    }
  }

  public void setPaintBusy(boolean paintBusy) {
    if (myBusy == paintBusy) return;

    myBusy = paintBusy;
    updateBusy();
  }

  private void updateBusy() {
    if (myBusy) {
      if (myBusyIcon == null) {
        myBusyIcon = new AsyncProcessIcon(toString()).setUseMask(false);
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        add(myBusyIcon);
        myBusyIcon.addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (!UIUtil.isActionClick(e)) return;
            AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(Tree.this);
            if (builder != null) {
              builder.cancelUpdate();
            }
          }
        });
      }
    }

    if (myBusyIcon != null) {
      if (myBusy) {
        if (shouldShowBusyIconIfNeeded()) {
          myBusyIcon.resume();
          myBusyIcon.setToolTipText("Update is in progress. Click to cancel");
        }
      }
      else {
        myBusyIcon.suspend();
        myBusyIcon.setToolTipText(null);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          if (myBusyIcon != null) {
            repaint();
          }
        });
      }
      updateBusyIconLocation();
    }
  }

  protected boolean shouldShowBusyIconIfNeeded() {
    // https://youtrack.jetbrains.com/issue/IDEA-101422 "Rotating wait symbol in Project list whenever typing"
    return hasFocus();
  }

  protected boolean paintNodes() {
    return false;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (paintNodes()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      paintNodeContent(g);
    }

    if (isFileColorsEnabled()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      paintFileColorGutter(g);
    }

    super.paintComponent(g);
    myEmptyText.paint(this, g);
  }

  protected void paintFileColorGutter(Graphics g) {
    GraphicsConfig config = new GraphicsConfig(g);
    Rectangle rect = getVisibleRect();
    int firstVisibleRow = getClosestRowForLocation(rect.x, rect.y);
    int lastVisibleRow = getClosestRowForLocation(rect.x, rect.y + rect.height);

    for (int row = firstVisibleRow; row <= lastVisibleRow; row++) {
      TreePath path = getPathForRow(row);
      Color color = path == null ? null : getFileColorForPath(path);
      if (color != null) {
        Rectangle bounds = getRowBounds(row);
        g.setColor(color);
        g.fillRect(0, bounds.y, getWidth(), bounds.height);
      }
    }
    config.restore();
  }

  @Nullable
  public Color getFileColorForPath(@NotNull TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof LoadingNode) {
      Object[] pathObjects = path.getPath();
      if (pathObjects.length > 1) {
        component = pathObjects[pathObjects.length - 2];
      }
    }
    return getFileColorFor(TreeUtil.getUserObject(component));
  }

  @Nullable
  public Color getFileColorFor(Object object) {
    return null;
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
  }

  /**
   * Hack to prevent loosing multiple selection on Mac when clicking Ctrl+Left Mouse Button.
   * See faulty code at BasicTreeUI.selectPathForEvent():2245
   * <p>
   * Another hack to match selection UI (wide) and selection behavior (narrow) in Nimbus/GTK+.
   */
  @Override
  protected void processMouseEvent(MouseEvent e) {
    MouseEvent e2 = e;

    if (SystemInfo.isMac) {
      if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && e.getID() == MouseEvent.MOUSE_PRESSED) {
        int modifiers = e.getModifiers() & ~(InputEvent.CTRL_MASK | InputEvent.BUTTON1_MASK) | InputEvent.BUTTON3_MASK;
        e2 = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), modifiers, e.getX(), e.getY(), e.getClickCount(),
                            true, MouseEvent.BUTTON3);
      }
    }
    else if (UIUtil.isUnderNimbusLookAndFeel() || UIUtil.isUnderGTKLookAndFeel()) {
      if (SwingUtilities.isLeftMouseButton(e) && (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_CLICKED)) {
        TreePath path = getClosestPathForLocation(e.getX(), e.getY());
        if (path != null) {
          Rectangle bounds = getPathBounds(path);
          if (bounds != null &&
              e.getY() > bounds.y && e.getY() < bounds.y + bounds.height &&
              (e.getX() >= bounds.x + bounds.width ||
               e.getX() < bounds.x && !isLocationInExpandControl(path, e.getX(), e.getY()))) {
            int newX = bounds.x + bounds.width - 2;
            e2 = MouseEventAdapter.convert(e, e.getComponent(), newX, e.getY());
          }
        }
      }
    }

    super.processMouseEvent(e2);
  }

  /**
   * Returns true if {@code mouseX} falls
   * in the area of row that is used to expand/collapse the node and
   * the node at {@code row} does not represent a leaf.
   */
  protected boolean isLocationInExpandControl(@Nullable TreePath path, int mouseX) {
    if (path == null) return false;
    Rectangle bounds = getRowBounds(getRowForPath(path));
    return isLocationInExpandControl(path, mouseX, bounds.y + bounds.height / 2);
  }


  private boolean isLocationInExpandControl(TreePath path, int x, int y) {
    TreeUI ui = getUI();
    if (!(ui instanceof BasicTreeUI)) return false;

    try {
      Class aClass = ui.getClass();
      while (BasicTreeUI.class.isAssignableFrom(aClass) && !BasicTreeUI.class.equals(aClass)) {
        aClass = aClass.getSuperclass();
      }
      Method method = ReflectionUtil.getDeclaredMethod(aClass, "isLocationInExpandControl", TreePath.class, int.class, int.class);
      if (method != null) {
        return (Boolean)method.invoke(ui, path, x, y);
      }
    }
    catch (Throwable ignore) {
    }

    return false;
  }

  /**
   * Disable Sun's speed search
   */
  @Override
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
  }

  private static final int AUTOSCROLL_MARGIN = 10;

  @Override
  public Insets getAutoscrollInsets() {
    return new Insets(getLocation().y + AUTOSCROLL_MARGIN, 0, getParent().getHeight() - AUTOSCROLL_MARGIN, getWidth() - 1);
  }

  @Override
  public void autoscroll(Point p) {
    int realRow = getClosestRowForLocation(p.x, p.y);
    if (getLocation().y + p.y <= AUTOSCROLL_MARGIN) {
      if (realRow >= 1) realRow--;
    }
    else {
      if (realRow < getRowCount() - 1) realRow++;
    }
    scrollRowToVisible(realRow);
  }

  protected boolean highlightSingleNode() {
    return true;
  }

  private void paintNodeContent(Graphics g) {
    if (!(getUI() instanceof BasicTreeUI)) return;

    AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(this);
    if (builder == null || builder.isDisposed()) return;

    GraphicsConfig config = new GraphicsConfig(g);
    config.setAntialiasing(true);

    AbstractTreeStructure structure = builder.getTreeStructure();

    for (int eachRow = 0; eachRow < getRowCount(); eachRow++) {
      TreePath path = getPathForRow(eachRow);
      PresentableNodeDescriptor node = toPresentableNode(path.getLastPathComponent());
      if (node == null) continue;

      if (!node.isContentHighlighted()) continue;

      if (highlightSingleNode()) {
        if (node.isContentHighlighted()) {
          TreePath nodePath = getPath(node);

          Rectangle rect;

          Rectangle parentRect = getPathBounds(nodePath);
          if (isExpanded(nodePath)) {
            int[] max = getMax(node, structure);
            rect = new Rectangle(parentRect.x,
                                 parentRect.y,
                                 Math.max((int)parentRect.getMaxX(), max[1]) - parentRect.x - 1,
                                 Math.max((int)parentRect.getMaxY(), max[0]) - parentRect.y - 1);
          }
          else {
            rect = parentRect;
          }

          if (rect != null) {
            Color highlightColor = node.getHighlightColor();
            g.setColor(highlightColor);
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
            g.setColor(highlightColor.darker());
            g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
          }
        }
      }
      else {
        //todo: to investigate why it might happen under 1.6: http://www.productiveme.net:8080/browse/PM-217
        if (node.getParentDescriptor() == null) continue;

        Object[] kids = structure.getChildElements(node);
        if (kids.length == 0) continue;

        PresentableNodeDescriptor first = null;
        PresentableNodeDescriptor last = null;
        int lastIndex = -1;
        for (int i = 0; i < kids.length; i++) {
          Object kid = kids[i];
          if (kid instanceof PresentableNodeDescriptor) {
            PresentableNodeDescriptor eachKid = (PresentableNodeDescriptor)kid;
            if (!node.isHighlightableContentNode(eachKid)) continue;
            if (first == null) {
              first = eachKid;
            }
            last = eachKid;
            lastIndex = i;
          }
        }

        if (first == null || last == null) continue;
        Rectangle firstBounds = getPathBounds(getPath(first));

        if (isExpanded(getPath(last))) {
          if (lastIndex + 1 < kids.length) {
            Object child = kids[lastIndex + 1];
            if (child instanceof PresentableNodeDescriptor) {
              PresentableNodeDescriptor nextKid = (PresentableNodeDescriptor)child;
              int nextRow = getRowForPath(getPath(nextKid));
              last = toPresentableNode(getPathForRow(nextRow - 1).getLastPathComponent());
            }
          }
          else {
            NodeDescriptor parentNode = node.getParentDescriptor();
            if (parentNode instanceof PresentableNodeDescriptor) {
              PresentableNodeDescriptor ppd = (PresentableNodeDescriptor)parentNode;
              int nodeIndex = node.getIndex();
              if (nodeIndex + 1 < structure.getChildElements(ppd).length) {
                PresentableNodeDescriptor nextChild = ppd.getChildToHighlightAt(nodeIndex + 1);
                int nextRow = getRowForPath(getPath(nextChild));
                TreePath prevPath = getPathForRow(nextRow - 1);
                if (prevPath != null) {
                  last = toPresentableNode(prevPath.getLastPathComponent());
                }
              }
              else {
                int lastRow = getRowForPath(getPath(last));
                PresentableNodeDescriptor lastParent = last;
                boolean lastWasFound = false;
                for (int i = lastRow + 1; i < getRowCount(); i++) {
                  PresentableNodeDescriptor eachNode = toPresentableNode(getPathForRow(i).getLastPathComponent());
                  if (!node.isParentOf(eachNode)) {
                    last = lastParent;
                    lastWasFound = true;
                    break;
                  }
                  lastParent = eachNode;
                }
                if (!lastWasFound) {
                  last = toPresentableNode(getPathForRow(getRowCount() - 1).getLastPathComponent());
                }
              }
            }
          }
        }

        if (last == null) continue;
        Rectangle lastBounds = getPathBounds(getPath(last));

        if (firstBounds == null || lastBounds == null) continue;

        Rectangle toPaint = new Rectangle(firstBounds.x, firstBounds.y, 0, (int)lastBounds.getMaxY() - firstBounds.y - 1);

        toPaint.width = getWidth() - toPaint.x - 4;

        Color highlightColor = first.getHighlightColor();
        g.setColor(highlightColor);
        g.fillRoundRect(toPaint.x, toPaint.y, toPaint.width, toPaint.height, 4, 4);
        g.setColor(highlightColor.darker());
        g.drawRoundRect(toPaint.x, toPaint.y, toPaint.width, toPaint.height, 4, 4);
      }
    }

    config.restore();
  }

  private int[] getMax(PresentableNodeDescriptor node, AbstractTreeStructure structure) {
    int x = 0;
    int y = 0;
    Object[] children = structure.getChildElements(node);
    for (Object child : children) {
      if (child instanceof PresentableNodeDescriptor) {
        TreePath childPath = getPath((PresentableNodeDescriptor)child);
        if (childPath != null) {
          if (isExpanded(childPath)) {
            int[] tmp = getMax((PresentableNodeDescriptor)child, structure);
            y = Math.max(y, tmp[0]);
            x = Math.max(x, tmp[1]);
          }

          Rectangle r = getPathBounds(childPath);
          if (r != null) {
            y = Math.max(y, (int)r.getMaxY());
            x = Math.max(x, (int)r.getMaxX());
          }
        }
      }
    }

    return new int[]{y, x};
  }

  @Nullable
  private static PresentableNodeDescriptor toPresentableNode(Object pathComponent) {
    if (!(pathComponent instanceof DefaultMutableTreeNode)) return null;
    Object userObject = ((DefaultMutableTreeNode)pathComponent).getUserObject();
    if (!(userObject instanceof PresentableNodeDescriptor)) return null;
    return (PresentableNodeDescriptor)userObject;
  }

  public TreePath getPath(PresentableNodeDescriptor node) {
    AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(this);
    DefaultMutableTreeNode treeNode = builder.getNodeForElement(node);

    return treeNode != null ? new TreePath(treeNode.getPath()) : new TreePath(node);
  }

  private static class MySelectionModel extends DefaultTreeSelectionModel {

    private TreePath[] myHeldSelection;

    @Override
    protected void fireValueChanged(TreeSelectionEvent e) {
      if (myHeldSelection == null) {
        super.fireValueChanged(e);
      }
    }

    public void holdSelection() {
      myHeldSelection = getSelectionPaths();
    }

    public void unholdSelection() {
      if (myHeldSelection != null) {
        setSelectionPaths(myHeldSelection);
        myHeldSelection = null;
      }
    }
  }

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent event) {
      setPressed(event, true);

      if (!JBSwingUtilities.isLeftMouseButton(event) &&
          (JBSwingUtilities.isRightMouseButton(event) || JBSwingUtilities.isMiddleMouseButton(event))) {
        TreePath path = getClosestPathForLocation(event.getX(), event.getY());
        if (path == null) return;

        Rectangle bounds = getPathBounds(path);
        if (bounds != null && bounds.y + bounds.height < event.getY()) return;

        if (getSelectionModel().getSelectionMode() != TreeSelectionModel.SINGLE_TREE_SELECTION) {
          TreePath[] selectionPaths = getSelectionModel().getSelectionPaths();
          if (selectionPaths != null) {
            for (TreePath selectionPath : selectionPaths) {
              if (selectionPath != null && selectionPath.equals(path)) return;
            }
          }
        }
        getSelectionModel().setSelectionPath(path);
      }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      setPressed(event, false);
      if (event.getButton() == MouseEvent.BUTTON1 &&
          event.getClickCount() == 2 &&
          isLocationInExpandControl(getClosestPathForLocation(event.getX(), event.getY()), event.getX())) {
        event.consume();
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (UIUtil.isUnderWin10LookAndFeel() && rollOverPath != null) {
        TreeCellRenderer renderer = getCellRenderer();
        if (rollOverPath.getLastPathComponent() instanceof TreeNode) {
          TreeNode node = (TreeNode)rollOverPath.getLastPathComponent();
          JComponent c = (JComponent)renderer.getTreeCellRendererComponent(
            Tree.this, node,
            isPathSelected(rollOverPath),
            isExpanded(rollOverPath),
            getModel().isLeaf(node),
            getRowForPath(rollOverPath), hasFocus());

          c.putClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY, null);
          rollOverPath = null;
          UIUtil.repaintViewport(Tree.this);
        }
      }
    }

    private void setPressed(MouseEvent e, boolean pressed) {
      if (UIUtil.isUnderWin10LookAndFeel()) {
        Point p = e.getPoint();
        TreePath path = getPathForLocation(p.x, p.y);
        if (path != null) {
          if (path.getLastPathComponent() instanceof TreeNode) {
            TreeNode node = (TreeNode)path.getLastPathComponent();
            JComponent c = (JComponent)getCellRenderer().getTreeCellRendererComponent(
              Tree.this, node,
              isPathSelected(path), isExpanded(path),
              getModel().isLeaf(node),
              getRowForPath(path), hasFocus());
            if (pressed) {
              c.putClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY, c instanceof JCheckBox ? getPathBounds(path) : node);
            }
            else {
              c.putClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY, null);
            }
            UIUtil.repaintViewport(Tree.this);
          }
        }
      }
    }
  }

  /**
   * This is patch for 4893787 SUN bug. The problem is that the BasicTreeUI.FocusHandler repaints
   * only lead selection index on focus changes. It's a problem with multiple selected nodes.
   */
  private class MyFocusListener extends FocusAdapter {
    private void focusChanges() {
      TreePath[] paths = getSelectionPaths();
      if (paths != null) {
        TreeUI ui = getUI();
        for (int i = paths.length - 1; i >= 0; i--) {
          Rectangle bounds = ui.getPathBounds(Tree.this, paths[i]);
          if (bounds != null) {
            repaint(bounds);
          }
        }
      }
    }

    @Override
    public void focusGained(FocusEvent e) {
      focusChanges();
    }

    @Override
    public void focusLost(FocusEvent e) {
      focusChanges();
    }
  }

  public final void setLineStyleAngled() {
    UIUtil.setLineStyleAngled(this);
  }

  @NotNull
  public <T> T[] getSelectedNodes(Class<T> nodeType, @Nullable NodeFilter<T> filter) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return (T[])Array.newInstance(nodeType, 0);

    ArrayList<T> nodes = new ArrayList<>();
    for (TreePath path : paths) {
      Object last = path.getLastPathComponent();
      if (nodeType.isAssignableFrom(last.getClass())) {
        if (filter != null && !filter.accept((T)last)) continue;
        nodes.add((T)last);
      }
    }
    T[] result = (T[])Array.newInstance(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }

  public interface NodeFilter<T> {
    boolean accept(T node);
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return;

    StringBuilder nodesText = new StringBuilder();

    for (TreePath eachPath : selection) {
      Object eachNode = eachPath.getLastPathComponent();
      Component c =
        getCellRenderer().getTreeCellRendererComponent(this, eachNode, false, false, false, getRowForPath(eachPath), false);

      if (c != null) {
        if (nodesText.length() > 0) {
          nodesText.append(";");
        }
        nodesText.append(c);
      }
    }

    if (nodesText.length() > 0) {
      info.put("selectedNodes", nodesText.toString());
    }
  }

  public void setHoldSize(boolean hold) {
    if (hold && myHoldSize == null) {
      myHoldSize = getPreferredSize();
    }
    else if (!hold && myHoldSize != null) {
      myHoldSize = null;
      revalidate();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();

    if (myHoldSize != null) {
      size.width = Math.max(size.width, myHoldSize.width);
      size.height = Math.max(size.height, myHoldSize.height);
    }

    return size;
  }

  public boolean isHorizontalAutoScrollingEnabled() {
    return myHorizontalAutoScrolling;
  }

  public void setHorizontalAutoScrollingEnabled(boolean enabled) {
    myHorizontalAutoScrolling = enabled;
  }

  /**
   * Returns the deepest visible component
   * that will be rendered at the specified location.
   *
   * @param x horizontal location in the tree
   * @param y vertical location in the tree
   * @return the deepest visible component of the renderer
   */
  @Nullable
  public Component getDeepestRendererComponentAt(int x, int y) {
    int row = getRowForLocation(x, y);
    if (row >= 0) {
      TreeCellRenderer renderer = getCellRenderer();
      if (renderer != null) {
        TreePath path = getPathForRow(row);
        Object node = path.getLastPathComponent();
        Component component = renderer.getTreeCellRendererComponent(
          this, node,
          isRowSelected(row),
          isExpanded(row),
          getModel().isLeaf(node),
          row, true);
        Rectangle bounds = getPathBounds(path);
        if (bounds != null) {
          component.setBounds(bounds); // initialize size to layout complex renderer
          return SwingUtilities.getDeepestComponentAt(component, x - bounds.x, y - bounds.y);
        }
      }
    }
    return null;
  }
}