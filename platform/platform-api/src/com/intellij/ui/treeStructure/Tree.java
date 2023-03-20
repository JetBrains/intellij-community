// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.SmoothAutoScroller;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.tree.TreePathBackgroundSupplier;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;

public class Tree extends JTree implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer>, Queryable,
                                           ComponentWithFileColors, TreePathBackgroundSupplier {
  /**
   * Force the following strategy for selection on the right click:
   * <ul>
   *   <li> If set to <b>false</b> or <b>true</b> and the only one path selected - change selection to the path under the mouse hover. </li>
   *   <li> If set to <b>false</b> and the multiple paths selected - do not change selection. </li>
   *   <li> If set to <b>true</b> and the multiple paths selected - do not change selection unless selected some path outside the current selection. </li>
   * </ul>
   *
   * Such strategy similar to {@link com.intellij.ui.table.JBTable}
   */
  @ApiStatus.Internal
  public static final Key<Boolean> AUTO_SELECT_ON_MOUSE_PRESSED = Key.create("allows to select a node automatically on right click");
  @ApiStatus.Internal
  public static final Key<Boolean> AUTO_SCROLL_FROM_SOURCE_BLOCKED = Key.create("auto scroll from source temporarily blocked");

  private final StatusText myEmptyText;
  private final ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;
  private Rectangle myLastVisibleRec;

  private Dimension myHoldSize;
  private final MySelectionModel mySelectionModel = new MySelectionModel();
  private ThreeState myHorizontalAutoScrolling = ThreeState.UNSURE;

  private TreePath rollOverPath;

  private final Timer autoScrollUnblockTimer = TimerUtil.createNamedTimer("TreeAutoscrollUnblock", 500, e -> unblockAutoScrollFromSource());

  public Tree() {
    this(new DefaultMutableTreeNode());
  }

  public Tree(TreeNode root) {
    this(new DefaultTreeModel(root, false));
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return Tree.this.isEmptyTextVisible();
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
            if (newPath.getLastPathComponent() instanceof TreeNode node) {
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

    putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, myEmptyText.getWrappedFragmentsIterable());
  }

  @Override
  public void setUI(TreeUI ui) {
    super.setUI(ui);
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  public boolean isEmpty() {
    return 0 >= getRowCount();
  }

  protected boolean isWideSelection() {
    return true;
  }

  /**
   * @return a strategy which determines if a wide selection should be drawn for a target row (it's number is
   * {@link Condition#value(Object) given} as an argument to the strategy)
   */
  @NotNull
  protected Condition<Integer> getWideSelectionBackgroundCondition() {
    return Conditions.alwaysTrue();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return false;
  }

  protected boolean isEmptyTextVisible() {
    return isEmpty();
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
    return isBackgroundSet() ? super.getBackground() : UIUtil.getTreeBackground();
  }

  @Override
  public Color getForeground() {
    return isForegroundSet() ? super.getForeground() : UIUtil.getTreeForeground();
  }

  @Override
  public void addNotify() {
    super.addNotify();

    // hack to invalidate sizes, see BasicTreeUI.Handler.propertyChange
    // now the sizes calculated before the tree has the correct GraphicsConfiguration and may be incorrect on the secondary display
    // see IDEA-184010
    firePropertyChange("font", null, null);

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

    if (!AbstractTreeBuilder.isToPaintSelection(this)) {
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
            }
            canHoldSelection = true;
            if (!myBusy || myBusyIcon == null) break;
          }
        }
      }

      if (canHoldSelection) {
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
        myBusyIcon = new AsyncProcessIcon(toString());
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
          myBusyIcon.setToolTipText(IdeBundle.message("tooltip.text.update.is.in.progress.click.to.cancel"));
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
    config.setupAAPainting();
    Rectangle rect = getVisibleRect();
    int firstVisibleRow = getClosestRowForLocation(rect.x, rect.y);
    int lastVisibleRow = getClosestRowForLocation(rect.x, rect.y + rect.height);

    Color prevColor = firstVisibleRow == 0 ? null : getFileColorForRow(firstVisibleRow - 1);
    Color curColor = getFileColorForRow(firstVisibleRow);
    Color nextColor = firstVisibleRow + 1 < getRowCount() ? getFileColorForRow(firstVisibleRow + 1) : null;
    for (int row = firstVisibleRow; row <= lastVisibleRow; row++) {
      nextColor = row + 1 < getRowCount() ? getFileColorForRow(row + 1) : null;
      if (curColor != null) {
        Rectangle bounds = getRowBounds(row);
        double x = JBUI.scale(4);
        double y = bounds.y;
        double w = JBUI.scale(4);
        double h = bounds.height;
        if (Registry.is("ide.file.colors.at.left")) {
          g.setColor(curColor);
          if (curColor.equals(prevColor) && curColor.equals(nextColor)) {
            RectanglePainter2D.FILL.paint((Graphics2D)g, x, y, w, h);
          } else if (!curColor.equals(prevColor) && !curColor.equals(nextColor)) {
            RectanglePainter2D.FILL.paint((Graphics2D)g, x, y + 2, w, h - 4, w);
          } else if (curColor.equals(prevColor)) {
            RectanglePainter2D.FILL.paint((Graphics2D)g, x, y - w, w, h + w - 2, w);
          } else {
            RectanglePainter2D.FILL.paint((Graphics2D)g, x, y + 2, w, h + w, w);
          }
        } else {
          g.setColor(curColor);
          g.fillRect(0, bounds.y, getWidth(), bounds.height);
        }
      }
      prevColor = curColor;
      curColor = nextColor;
    }
    config.restore();
  }

  @Override
  @Nullable
  public Color getPathBackground(@NotNull TreePath path, int row) {
    return isFileColorsEnabled() && !Registry.is("ide.file.colors.at.left") ? getFileColorForPath(path) : null;
  }

  @Nullable
  public Color getFileColorForRow(int row) {
    TreePath path = getPathForRow(row);
    return path != null ? getFileColorForPath(path) : null;
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
      e2 = MacUIUtil.fixMacContextMenuIssue(e);
    }

    super.processMouseEvent(e2);

    if (e != e2 && e2.isConsumed()) e.consume();
  }

  /**
   * Returns true if {@code mouseX} falls
   * in the area of row that is used to expand/collapse the node and
   * the node at {@code row} does not represent a leaf.
   */
  @ApiStatus.Experimental
  protected boolean isLocationInExpandControl(@Nullable TreePath path, int mouseX) {
    if (path == null) return false;
    Rectangle bounds = getRowBounds(getRowForPath(path));
    return TreeUtil.isLocationInExpandControl(this, path, mouseX, bounds.y + bounds.height / 2);
  }

  /**
   * Disable Sun's speed search
   */
  @Override
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
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
      PresentableNodeDescriptor<?> node = TreeUtil.getLastUserObject(PresentableNodeDescriptor.class, path);
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
          if (kid instanceof PresentableNodeDescriptor eachKid) {
            if (!node.isHighlightableContentNode(eachKid)) continue;
            if (first == null) {
              first = eachKid;
            }
            last = eachKid;
            lastIndex = i;
          }
        }

        if (first == null) continue;
        Rectangle firstBounds = getPathBounds(getPath(first));

        if (isExpanded(getPath(last))) {
          if (lastIndex + 1 < kids.length) {
            Object child = kids[lastIndex + 1];
            if (child instanceof PresentableNodeDescriptor nextKid) {
              int nextRow = getRowForPath(getPath(nextKid));
              last = TreeUtil.getLastUserObject(PresentableNodeDescriptor.class, getPathForRow(nextRow - 1));
            }
          }
          else {
            NodeDescriptor parentNode = node.getParentDescriptor();
            if (parentNode instanceof PresentableNodeDescriptor ppd) {
              int nodeIndex = node.getIndex();
              if (nodeIndex + 1 < structure.getChildElements(ppd).length) {
                PresentableNodeDescriptor nextChild = ppd.getChildToHighlightAt(nodeIndex + 1);
                int nextRow = getRowForPath(getPath(nextChild));
                TreePath prevPath = getPathForRow(nextRow - 1);
                if (prevPath != null) {
                  last = TreeUtil.getLastUserObject(PresentableNodeDescriptor.class, prevPath);
                }
              }
              else {
                int lastRow = getRowForPath(getPath(last));
                PresentableNodeDescriptor lastParent = last;
                boolean lastWasFound = false;
                for (int i = lastRow + 1; i < getRowCount(); i++) {
                  PresentableNodeDescriptor<?> eachNode = TreeUtil.getLastUserObject(PresentableNodeDescriptor.class, getPathForRow(i));
                  if (!node.isParentOf(eachNode)) {
                    last = lastParent;
                    lastWasFound = true;
                    break;
                  }
                  lastParent = eachNode;
                }
                if (!lastWasFound) {
                  last = TreeUtil.getLastUserObject(PresentableNodeDescriptor.class, getPathForRow(getRowCount() - 1));
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

  public TreePath getPath(@NotNull PresentableNodeDescriptor node) {
    AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(this);
    DefaultMutableTreeNode treeNode = builder.getNodeForElement(node);

    return treeNode != null ? new TreePath(treeNode.getPath()) : new TreePath(node);
  }

  @Override
  public void collapsePath(TreePath path) {
    int row = AdvancedSettings.getBoolean("ide.tree.collapse.recursively") ? getRowForPath(path) : -1;
    if (row < 0) {
      super.collapsePath(path);
    }
    else if (!isAlwaysExpanded(path)) {
      ArrayDeque<TreePath> deque = new ArrayDeque<>();
      deque.addFirst(path);
      while (++row < getRowCount()) {
        TreePath next = getPathForRow(row);
        if (!path.isDescendant(next)) break;
        if (isExpanded(next)) deque.addFirst(next);
      }
      deque.forEach(super::collapsePath);
    }
  }

  private boolean isAlwaysExpanded(TreePath path) {
    return path != null && TreeUtil.getNodeDepth(this, path) <= 0;
  }

  private void blockAutoScrollFromSource() {
    ClientProperty.put(this, AUTO_SCROLL_FROM_SOURCE_BLOCKED, true);
    autoScrollUnblockTimer.restart();
  }

  @ApiStatus.Internal
  public void unblockAutoScrollFromSource() {
    ClientProperty.remove(this, AUTO_SCROLL_FROM_SOURCE_BLOCKED);
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

    private MyMouseListener() {
      autoScrollUnblockTimer.setRepeats(false);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      if (!hasFocus()) {
        blockAutoScrollFromSource();
      }

      setPressed(event, true);

      if (Boolean.FALSE.equals(UIUtil.getClientProperty(event.getSource(), AUTO_SELECT_ON_MOUSE_PRESSED))
          && getSelectionModel().getSelectionCount() > 1) {
        return;
      }
      if (!SwingUtilities.isLeftMouseButton(event) &&
          (SwingUtilities.isRightMouseButton(event) || SwingUtilities.isMiddleMouseButton(event))) {
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
          TreeUtil.isLocationInExpandControl(Tree.this, event.getX(), event.getY())) {
        event.consume();
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (UIUtil.isUnderWin10LookAndFeel() && rollOverPath != null) {
        TreeCellRenderer renderer = getCellRenderer();
        if (rollOverPath.getLastPathComponent() instanceof TreeNode node) {
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
          if (path.getLastPathComponent() instanceof TreeNode node) {
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

  /**
   * @deprecated no effect
   */
  @Deprecated
  public final void setLineStyleAngled() {
  }

  public <T> T @NotNull [] getSelectedNodes(Class<T> nodeType, @Nullable NodeFilter<? super T> filter) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return ArrayUtil.newArray(nodeType, 0);

    ArrayList<T> nodes = new ArrayList<>();
    for (TreePath path : paths) {
      Object last = path.getLastPathComponent();
      if (nodeType.isAssignableFrom(last.getClass())) {
        if (filter != null && !filter.accept((T)last)) continue;
        nodes.add((T)last);
      }
    }
    T[] result = ArrayUtil.newArray(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }

  public interface NodeFilter<T> {
    boolean accept(T node);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
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

  @Override
  public void scrollPathToVisible(@Nullable TreePath path) {
    if (path == null) return; // nothing to scroll
    makeVisible(path); // expand parent paths if needed
    TreeUtil.scrollToVisible(this, path, false);
  }

  public boolean isHorizontalAutoScrollingEnabled() {
    return myHorizontalAutoScrolling != ThreeState.UNSURE ? myHorizontalAutoScrolling == ThreeState.YES : Registry.is("ide.tree.horizontal.default.autoscrolling", false);
  }

  public void setHorizontalAutoScrollingEnabled(boolean enabled) {
    myHorizontalAutoScrolling = enabled ? ThreeState.YES : ThreeState.NO;
  }

  /**
   * @see com.intellij.ui.table.JBTable#getScrollableUnitIncrement(Rectangle, int, int)
   */
  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    int increment = super.getScrollableUnitIncrement(visibleRect, orientation, direction);
    if (increment == 0 && orientation == SwingConstants.VERTICAL && direction < 0) {
      return visibleRect.y; // BasicTreeUI.getPathBounds includes insets, not allowing to reach 0 with mouse wheel.
    }
    return increment;
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
          component.doLayout();
          return SwingUtilities.getDeepestComponentAt(component, x - bounds.x, y - bounds.y);
        }
      }
    }
    return null;
  }

  @Override
  public void setTransferHandler(TransferHandler handler) {
    SmoothAutoScroller.installDropTargetAsNecessary(this);
    super.setTransferHandler(handler);
  }
}
