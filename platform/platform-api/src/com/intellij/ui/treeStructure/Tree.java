// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import com.intellij.ide.ActivityTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.SmoothAutoScroller;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.treeView.CachedTreePresentation;
import com.intellij.ide.util.treeView.CachedTreePresentationSupport;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.tree.TreePathBackgroundSupplier;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.*;
import javax.swing.plaf.TreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputMethodRequests;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;

public class Tree extends JTree implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer>, Queryable,
                                           ComponentWithFileColors, TreePathBackgroundSupplier, CachedTreePresentationSupport {
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
  private static final @NotNull Logger LOG = Logger.getInstance(Tree.class);

  private final StatusText myEmptyText;
  private final ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;
  private Rectangle myLastVisibleRec;

  private Dimension myHoldSize;

  private int myAdditionalRowsCount = -1;
  private final MySelectionModel mySelectionModel = new MySelectionModel();
  private ThreeState myHorizontalAutoScrolling = ThreeState.UNSURE;

  private TreePath rollOverPath;

  private final Timer autoScrollUnblockTimer = TimerUtil.createNamedTimer("TreeAutoscrollUnblock", 500, e -> unblockAutoScrollFromSource());

  private final @NotNull Tree.ExpandImpl expandImpl;
  private final @NotNull AtomicInteger suspendedExpandAccessibilityAnnouncements = new AtomicInteger();
  private final @NotNull AtomicInteger bulkOperationsInProgress = new AtomicInteger();
  private final @NotNull AtomicBoolean applyingViewModelChanges = new AtomicBoolean();
  private transient boolean settingUI;
  private transient TreeExpansionListener uiTreeExpansionListener;

  private final @NotNull AtomicInteger processingDoubleClick = new AtomicInteger();

  private final @NotNull MyUISettingsListener myUISettingsListener = new MyUISettingsListener();

  private boolean initialized = false;

  @ApiStatus.Internal
  public static boolean isBulkExpandCollapseSupported() {
    return true;
  }

  @ApiStatus.Internal
  public static boolean isExpandWithSingleClickSettingEnabled() {
    return Registry.is("ide.tree.show.expand.with.single.click.setting", true);
  }

  private static boolean isCollapseRecursively() {
    //noinspection SimplifiableConditionalExpression // no, dear inspection, using || here does NOT make the code more readable
    return ApplicationManager.getApplication() != null // could be null, e.g. in tests
      ? AdvancedSettings.getBoolean("ide.tree.collapse.recursively")
      : true;
  }

  public Tree() {
    this(new DefaultMutableTreeNode());
  }

  public Tree(TreeNode root) {
    this(new DefaultTreeModel(root, false));
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    // An ugly hijacking: SmartExpander can't access advanced settings by itself, so we use the Tree constructor
    // as a convenient place to put this code somewhere where it'll be surely executed by the time it's needed.
    // We also update this setting in com.intellij.ui.tree.RecursiveExpandSettingListener.
    SmartExpander.setRecursiveCollapseEnabled(isCollapseRecursively());
    expandImpl = new ExpandImpl();
    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return Tree.this.isEmptyTextVisible();
      }
    };

    myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);

    initialized = true; // A flag to avoid NPE when accessing fields from methods called from the super() constructor.

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
    // We have to repeat what JTree does here, because uiTreeExpansionListener is private in JTree.
    if (this.ui != ui) {
      settingUI = true;
      uiTreeExpansionListener = null;
      try {
        super.setUI(ui);
      }
      finally {
        settingUI = false;
      }
    }
  }

  @Override
  public void setToggleClickCount(int clickCount) {
    super.setToggleClickCount(clickCount);
    myUISettingsListener.setToggleClickCountCalled();
  }

  @Override
  public void addTreeExpansionListener(TreeExpansionListener listener) {
    if (settingUI) {
      uiTreeExpansionListener = listener;
    }
    super.addTreeExpansionListener(listener);
  }

  @Override
  public void removeTreeExpansionListener(TreeExpansionListener listener) {
    super.removeTreeExpansionListener(listener);
    if (uiTreeExpansionListener == listener) {
      uiTreeExpansionListener = null;
    }
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  /**
   * Starts measuring the next expand operation if the tree supports that
   * <p>
   *   Internal API for statistics. Called by the UI and certain actions right before a path is expanded.
   *   It's up to the tree then to stop measuring at the right moment and send the value to the collector.
   * </p>
   * @param path the path that will be expanded
   */
  @ApiStatus.Internal
  public void startMeasuringExpandDuration(@NotNull TreePath path) {
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
  protected @NotNull Condition<Integer> getWideSelectionBackgroundCondition() {
    return Conditions.alwaysTrue();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return false;
  }

  protected boolean isEmptyTextVisible() {
    return isEmpty();
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  public @NotNull ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
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
    myUISettingsListener.connect();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (myBusyIcon != null) {
      remove(myBusyIcon);
      Disposer.dispose(myBusyIcon);
      myBusyIcon = null;
    }
    myUISettingsListener.disconnect();
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
    boolean shouldPaintBusyIcon = myBusy && shouldShowBusyIconIfNeeded();

    if (shouldPaintBusyIcon) {
      if (myBusyIcon == null) {
        myBusyIcon = new AsyncProcessIcon(toString());
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        myBusyIcon.setToolTipText(IdeBundle.message("tooltip.text.update.is.in.progress.click.to.cancel"));
        add(myBusyIcon);
      }

      myBusyIcon.resume();
      myBusyIcon.setVisible(true);
      updateBusyIconLocation();
    }

    if (!shouldPaintBusyIcon && myBusyIcon != null) {
      myBusyIcon.suspend();
      myBusyIcon.setVisible(false);
      SwingUtilities.invokeLater(() -> {
        if (myBusyIcon != null) {
          repaint();
        }
      });
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
    Color nextColor;
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
  public @Nullable Color getPathBackground(@NotNull TreePath path, int row) {
    return isFileColorsEnabled() && !Registry.is("ide.file.colors.at.left") ? getFileColorForPath(path) : null;
  }

  public @Nullable Color getFileColorForRow(int row) {
    TreePath path = getPathForRow(row);
    return path != null ? getFileColorForPath(path) : null;
  }
  public @Nullable Color getFileColorForPath(@NotNull TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof LoadingNode) {
      Object[] pathObjects = path.getPath();
      if (pathObjects.length > 1) {
        component = pathObjects[pathObjects.length - 2];
      }
    }
    return getFileColorFor(TreeUtil.getUserObject(component));
  }

  public @Nullable Color getFileColorFor(Object object) {
    return null;
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
  }

  @Override
  public boolean getDragEnabled() {
    // Temporarily disable dragging to avoid bogus double click mouse events from
    // javax.swing.plaf.basic.DragRecognitionSupport, created in
    // javax.swing.plaf.basic.BasicTreeUI.Handler.mouseReleasedDND.
    return super.getDragEnabled() && processingDoubleClick.get() == 0;
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

    if (ClientSystemInfo.isMac()) {
      e2 = MacUIUtil.fixMacContextMenuIssue(e);
    }

    var isDoubleClick = e.getClickCount() >= 2;
    if (isDoubleClick) processingDoubleClick.incrementAndGet();
    try {
      super.processMouseEvent(e2);
    }
    finally {
      if (isDoubleClick) processingDoubleClick.decrementAndGet();
    }

    if (e != e2 && e2.isConsumed()) e.consume();
  }

  /**
   * Disable Sun's speed search
   */
  @Override
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
  }

  public TreePath getPath(@NotNull PresentableNodeDescriptor node) {
    return null; // TODO not implemented
  }

  @Nullable CachingTreePath getPath(@Nullable TreeModelEvent event) {
    if (event == null) return null;
    var path = event.getTreePath();
    var model = getModel();
    // mimic sun.swing.SwingUtilities2.getTreePath
    if ((path == null) && (model != null)) {
      Object root = model.getRoot();
      if (root != null) {
        return new CachingTreePath(root);
      }
    }
    return CachingTreePath.ensureCaching(path);
  }

  public void expandPaths(@NotNull Iterable<@NotNull TreePath> paths) {
    if (!initialized) { // Base constructor call.
      for (TreePath path : paths) {
        super.expandPath(path);
      }
      return;
    }
    expandImpl.expandPaths(paths);
  }

  @Override
  public void collapsePath(TreePath path) {
    int row = isCollapseRecursively() ? getRowForPath(path) : -1;
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
      collapsePaths(deque);
    }
  }

  public void collapsePaths(@NotNull Iterable<@NotNull TreePath> paths) {
    if (!initialized) { // Base constructor call.
      for (TreePath path : paths) {
        super.collapsePath(path);
      }
      return;
    }
    expandImpl.collapsePaths(paths);
  }

  private boolean isAlwaysExpanded(TreePath path) {
    return path != null && TreeUtil.getNodeDepth(this, path) <= 0;
  }

  /**
   * Suspends expand/collapse accessibility announcements.
   * <p>
   *   Normally, the tree fires "node expanded/collapsed" events for every expanded/collapsed node.
   *   However, if a lot of nodes are collapsed/expanded at the same time, it may make more sense to stop
   *   announcing every single operation and only announce the result in a more meaningful way once the operation
   *   is over. This method, along with {@link #resumeExpandCollapseAccessibilityAnnouncements()} is indended for such cases.
   * </p>
   * <p>
   *   To support recursive/reentrant expand/collapse operations, this method
   *   may be called multiple times, but then {@link #resumeExpandCollapseAccessibilityAnnouncements()} must
   *   be called exactly the same number of times, so both are best used in a try-finally block to avoid unpleasant
   *   surprises.
   * </p>
   */
  @ApiStatus.Internal
  public void suspendExpandCollapseAccessibilityAnnouncements() {
    suspendedExpandAccessibilityAnnouncements.incrementAndGet();
  }

  /**
   * Resumes expand/collapse accessibility announcements.
   * <p>
   *   To support recursive/reentrant expand/collapse operation, this method
   *   must be called exactly the same number of times as {@link #suspendExpandCollapseAccessibilityAnnouncements()},
   *   so both are best used in a try-finally block to avoid unpleasant surprises.
   * </p>
   */
  @ApiStatus.Internal
  public void resumeExpandCollapseAccessibilityAnnouncements() {
    suspendedExpandAccessibilityAnnouncements.decrementAndGet();
  }

  /**
   * Fires a tree expanded event to the accessibility subsystem.
   * <p>
   *   Intended to be used together with {@link #suspendExpandCollapseAccessibilityAnnouncements()}
   *   and {@link #resumeExpandCollapseAccessibilityAnnouncements()} for complex expand/collapse operations:
   *   first announcements are suspended, then they're resumed and this method
   *   (or {@link #fireAccessibleTreeCollapsed(TreePath)} is called for the paths that are actually supposed to be announced.
   * </p>
   * @param path the path that has been expanded
   */
  @ApiStatus.Internal
  public void fireAccessibleTreeExpanded(@NotNull TreePath path) {
    if (accessibleContext != null) {
      ((AccessibleJTree)accessibleContext).treeExpanded(new TreeExpansionEvent(this, path));
    }
  }

  /**
   * Fires a tree collapsed event to the accessibility subsystem.
   * <p>
   *   Intended to be used together with {@link #suspendExpandCollapseAccessibilityAnnouncements()}
   *   and {@link #resumeExpandCollapseAccessibilityAnnouncements()} for complex expand/collapse operations:
   *   first announcements are suspended, then they're resumed and this method
   *   (or {@link #fireAccessibleTreeExpanded(TreePath)} is called for the paths that are actually supposed to be announced.
   * </p>
   * @param path the path that has been collapsed
   */
  @ApiStatus.Internal
  public void fireAccessibleTreeCollapsed(@NotNull TreePath path) {
    if (accessibleContext != null) {
      ((AccessibleJTree)accessibleContext).treeCollapsed(new TreeExpansionEvent(this, path));
    }
  }

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    var model = treeModel;
    if (TREE_MODEL_PROPERTY.equals(propertyName)) {
      if (oldValue instanceof CachedTreePresentationSupport cps) {
        cps.setCachedPresentation(null);
      }
      // This whole thing can be called from the super() constructor.
      // In this case we migrate this "root expanded" state later, in the ExpandImpl constructor.
      if (initialized && model != null) {
        Object treeRoot = model.getRoot();
        if (treeRoot != null && !model.isLeaf(treeRoot)) {
          super.clearToggledPaths(); // to clear JTree.expandedState populated by JTree.setModel(), to avoid leaks
          expandImpl.markPathExpanded(new CachingTreePath(treeRoot));
        }
      }
      // And this thing shouldn't do anything if called from the super() constructor,
      // because at that point the cached presentation can't possibly be set yet.
      if (initialized && newValue instanceof CachedTreePresentationSupport cps) {
        cps.setCachedPresentation(expandImpl.getCachedPresentation());
      }
    }
    super.firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public void fireTreeExpanded(@NotNull TreePath path) {
    Object[] listeners = listenerList.getListenerList();
    TreeExpansionEvent e = new TreeBulkExpansionEvent(this, path, isBulkOperationInProgress());
    if (uiTreeExpansionListener != null) {
      uiTreeExpansionListener.treeExpanded(e);
    }
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class &&
        listeners[i + 1] != uiTreeExpansionListener &&
        (listeners[i + 1] != accessibleContext || expandAccessibilityAnnouncementsAllowed())
      ) {
        ((TreeExpansionListener)listeners[i + 1]).treeExpanded(e);
      }
    }
  }

  @Override
  public void fireTreeCollapsed(@NotNull TreePath path) {
    Object[] listeners = listenerList.getListenerList();
    TreeExpansionEvent e = new TreeBulkExpansionEvent(this, path, isBulkOperationInProgress());
    if (uiTreeExpansionListener != null) {
      uiTreeExpansionListener.treeCollapsed(e);
    }
    for (int i = listeners.length - 2; i>=0; i-=2) {
      if (
        listeners[i] == TreeExpansionListener.class &&
        listeners[i + 1] != uiTreeExpansionListener &&
        (listeners[i + 1] != accessibleContext || expandAccessibilityAnnouncementsAllowed())
      ) {
        ((TreeExpansionListener)listeners[i + 1]).treeCollapsed(e);
      }
    }
  }

  private boolean isBulkOperationInProgress() {
    return initialized && bulkOperationsInProgress.get() > 0;
  }

  private void fireBulkExpandStarted() {
    Object[] listeners = listenerList.getListenerList();
    TreeBulkExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeBulkExpansionListener bulkExpansionListener
      ) {
        if (e == null) {
           e = new TreeBulkExpansionEvent(this, null, false);
        }
        bulkExpansionListener.treeBulkExpansionStarted(e);
      }
    }
  }

  private void fireBulkExpandEnded() {
    Object[] listeners = listenerList.getListenerList();
    TreeBulkExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeBulkExpansionListener bulkExpansionListener
      ) {
        if (e == null) {
           e = new TreeBulkExpansionEvent(this, null, false);
        }
        bulkExpansionListener.treeBulkExpansionEnded(e);
      }
    }
  }

  private void fireBulkCollapseStarted() {
    Object[] listeners = listenerList.getListenerList();
    TreeBulkExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeBulkExpansionListener bulkExpansionListener
      ) {
        if (e == null) {
           e = new TreeBulkExpansionEvent(this, null, false);
        }
        bulkExpansionListener.treeBulkCollapseStarted(e);
      }
    }
  }

  private void fireBulkCollapseEnded() {
    Object[] listeners = listenerList.getListenerList();
    TreeBulkExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeBulkExpansionListener bulkExpansionListener
      ) {
        if (e == null) {
           e = new TreeBulkExpansionEvent(this, null, false);
        }
        bulkExpansionListener.treeBulkCollapseEnded(e);
      }
    }
  }

  @ApiStatus.Internal
  public void fireTreeStateRestoreStarted() {
    Object[] listeners = listenerList.getListenerList();
    TreeExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeStateListener stateListener
      ) {
        if (e == null) {
           e = new TreeExpansionEvent(this, null);
        }
        stateListener.treeStateRestoreStarted(e);
      }
    }
  }

  @ApiStatus.Internal
  public void fireTreeStateCachedStateRestored() {
    Object[] listeners = listenerList.getListenerList();
    TreeExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeStateListener stateListener
      ) {
        if (e == null) {
           e = new TreeExpansionEvent(this, null);
        }
        stateListener.treeStateCachedStateRestored(e);
      }
    }
  }

  @ApiStatus.Internal
  public void fireTreeStateRestoreFinished() {
    Object[] listeners = listenerList.getListenerList();
    TreeExpansionEvent e = null;
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (
        listeners[i] == TreeExpansionListener.class
        && listeners[i + 1] instanceof TreeStateListener stateListener
      ) {
        if (e == null) {
           e = new TreeExpansionEvent(this, null);
        }
        stateListener.treeStateRestoreFinished(e);
      }
    }
  }

  private boolean expandAccessibilityAnnouncementsAllowed() {
    return suspendedExpandAccessibilityAnnouncements.get() == 0;
  }

  public @NotNull Set<TreePath> getExpandedPaths() {
    if (!initialized) { // Called from a super constructor.
      var rootPath = getRootPath();
      if (rootPath == null || !super.isExpanded(rootPath)) return Collections.emptySet();
      var result = new HashSet<TreePath>();
      result.add(rootPath);
      var more = super.getExpandedDescendants(rootPath);
      if (more != null) {
        while (more.hasMoreElements()) {
          result.add(more.nextElement());
        }
      }
      return result;
    }
    return expandImpl.getExpandedPaths();
  }

  private @Nullable TreePath getRootPath() {
    var model = treeModel;
    if (model == null) {
      return null;
    }
    var rootObject = model.getRoot();
    if (rootObject == null) {
      return null;
    }
    return new CachingTreePath(rootObject);
  }

  @Override
  public Enumeration<TreePath> getExpandedDescendants(TreePath parent) {
    if (!initialized) { // When called from the super() constructor, the root may already be expanded.
      return super.getExpandedDescendants(parent);
    }
    return expandImpl.getExpandedDescendants(parent);
  }

  @Override
  public boolean hasBeenExpanded(TreePath path) {
    if (!initialized) { // Called from a super constructor.
      return super.hasBeenExpanded(path);
    }
    return expandImpl.hasBeenExpanded(path);
  }

  @Override
  public boolean isExpanded(TreePath path) {
    if (!initialized) { // Called from the super() constructor.
      return super.isExpanded(path);
    }
    return expandImpl.isExpanded(path);
  }

  @Override
  public boolean isExpanded(int row) {
    if (!initialized) { // Called from the super() constructor.
      return super.isExpanded(row);
    }
    return expandImpl.isExpanded(row);
  }

  @Override
  protected void setExpandedState(TreePath path, boolean state) {
    if (!initialized) { // Called from the super() constructor.
      super.setExpandedState(path, state);
      return;
    }
    expandImpl.setExpandedState(path, state);
  }

  @Override
  protected Enumeration<TreePath> getDescendantToggledPaths(TreePath parent) {
    if (!initialized) { // Called from the super() constructor.
      return super.getDescendantToggledPaths(parent);
    }
    return expandImpl.getDescendantToggledPaths(parent);
  }

  @Override
  protected void removeDescendantToggledPaths(Enumeration<TreePath> toRemove)
  {
    if (!initialized) { // Called from the super() constructor.
      super.removeDescendantToggledPaths(toRemove);
      return;
    }
    expandImpl.removeDescendantToggledPaths(toRemove);
  }

  @Override
  protected void clearToggledPaths() {
    if (initialized) { // If called from the super() constructor, do nothing here because there's no state to clear yet.
      expandImpl.clearToggledPaths();
    }
    // Technically, we only need this if expandImpl == null,
    // but in theory it might happen that some code in JTree that
    // we forgot to override might add something to expandImpl,
    // and that may cause leaks like IJPL-165735, so better make sure it's cleared anyway.
    super.clearToggledPaths();
  }

  @Override
  protected TreeModelListener createTreeModelListener() {
    // Can't just create a listener here because this function
    // is called from a base class constructor, so our class isn't initialized yet.
    return new MyTreeModelListener();
  }

  private void blockAutoScrollFromSource() {
    ClientProperty.put(this, AUTO_SCROLL_FROM_SOURCE_BLOCKED, true);
    autoScrollUnblockTimer.restart();
  }

  @ApiStatus.Internal
  public void unblockAutoScrollFromSource() {
    ClientProperty.remove(this, AUTO_SCROLL_FROM_SOURCE_BLOCKED);
  }

  private class MySelectionModel extends DefaultTreeSelectionModel {

    private TreePath[] myHeldSelection;

    @Override
    protected void fireValueChanged(TreeSelectionEvent e) {
      if (myHeldSelection == null) {
        ActivityTracker.getInstance().inc();
        super.fireValueChanged(e);
        if (!applyingViewModelChanges.get() && treeModel instanceof TreeSwingModel swingModel) {
          var newSelection = new ArrayList<TreeNodeViewModel>();
          for (TreePath path : getSelectionPaths()) {
            if (path.getLastPathComponent() instanceof TreeNodeViewModel viewModel) {
              newSelection.add(viewModel);
            }
          }
          swingModel.getViewModel().setSelection(newSelection);
        }
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
    private @Nullable TreePath treePathUnderMouse = null;

    private MyMouseListener() {
      autoScrollUnblockTimer.setRepeats(false);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      treePathUnderMouse = getPathForLocation(event.getX(), event.getY());

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
      TreePath treePathUnderMouseAfterEvent = getPathForLocation(event.getX(), event.getY());
      if (!Comparing.equal(treePathUnderMouse, treePathUnderMouseAfterEvent)) {
        event.consume(); // IDEA-338787: BasicTreeUI.checkForClickInExpandControl does not consume the event
      }
      treePathUnderMouse = null;

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
        if (!nodesText.isEmpty()) {
          nodesText.append(";");
        }
        nodesText.append(c);
      }
    }

    if (!nodesText.isEmpty()) {
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

  /**
   * Sets the number of extra empty rows at the bottom of the tree.
   * <p>
   *   The extra rows are empty and only displayed to improve readability and reduce clutter
   *   at the bottom of the tree in case it borders a complex component like a toolbar.
   * </p>
   * @param additionalRowsCount the number of extra empty rows (possibly zero), or {@code -1} to use the default value
   * @see #getAdditionalRowsCount()
   * @see #getEffectiveAdditionalRowsCount()
   */
  public void setAdditionalRowsCount(int additionalRowsCount) {
    int oldValue = myAdditionalRowsCount;
    myAdditionalRowsCount = additionalRowsCount;
    firePropertyChange("additionalRowsCount", oldValue, additionalRowsCount);
  }

  /**
   * Returns the number of extra empty rows at the bottom of the tree.
   * <p>
   *   Note that by default it's set to {@code -1} which means "Use the default value".
   *   Call {@link #getEffectiveAdditionalRowsCount()} to get the number of rows that will actually be displayed.
   * </p>
   * @return the number of extra rows or {@code -1} if the default value is in effect
   * @see #setAdditionalRowsCount(int)
   * @see #getEffectiveAdditionalRowsCount()
   */
  public int getAdditionalRowsCount() {
    return myAdditionalRowsCount;
  }

  /**
   * Returns the actual number of extra empty rows at the bottom of the tree.
   * <p>
   *   The difference between this and {@link #getAdditionalRowsHeight()} is that the latter returns {@code -1}
   *   if the default value is in effect, while this one returns the actual default value if that's the case.
   * </p>
   * @return the number of extra rows
   * @see #setAdditionalRowsCount(int)
   * @see #getAdditionalRowsHeight()
   */
  public int getEffectiveAdditionalRowsCount() {
    var result = myAdditionalRowsCount;
    if (result == -1) {
      result = Registry.intValue("ide.tree.additional.rows.count", 1, 0, 10);
    }
    return result;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();

    size.height += getAdditionalRowsHeight();

    if (myHoldSize != null) {
      size.width = Math.max(size.width, myHoldSize.width);
      size.height = Math.max(size.height, myHoldSize.height);
    }

    return size;
  }

  private int getAdditionalRowsHeight() {
    var additionalRowsCount = getEffectiveAdditionalRowsCount();
    if (additionalRowsCount == 0) {
      return 0;
    }
    var rowHeight = getDefaultRowHeight();
    if (rowHeight == 0) {
      return 0;
    }
    var extraHeight = rowHeight * additionalRowsCount;
    var viewport = ComponentUtil.getViewport(this);
    var viewportHeight = viewport == null ? 0 : viewport.getHeight();
    var maximumSensibleExtraHeight = viewportHeight - rowHeight;
    if (maximumSensibleExtraHeight < 0) {
      maximumSensibleExtraHeight = 0;
    }
    return Math.min(extraHeight, maximumSensibleExtraHeight);
  }

  private int getDefaultRowHeight() {
    var result = getRowHeight();
    if (result <= 0) {
      result = JBUI.CurrentTheme.Tree.rowHeight();
    }
    if (result <= 0) {
      result = 0;
    }
    return result;
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
  public @Nullable Component getDeepestRendererComponentAt(int x, int y) {
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

  @Override
  public InputMethodRequests getInputMethodRequests() {
    SpeedSearchSupply supply = SpeedSearchSupply.getSupply(this, true);
    if (supply == null) {
      return null;
    } else {
      return supply.getInputMethodRequests();
    }
  }

  @ApiStatus.Internal
  @Override
  public void setCachedPresentation(@Nullable CachedTreePresentation presentation) {
    expandImpl.setCachedPresentation(presentation);
  }

  private class CachedPresentationImpl {
    private final @NotNull CachedTreePresentation cachedTree;

    CachedPresentationImpl(@NotNull CachedTreePresentation cachedTree) {
      this.cachedTree = cachedTree;
    }

    void setExpanded(@NotNull TreePath path, boolean isExpanded) {
      cachedTree.setExpanded(path, isExpanded);
    }

    void updateExpandedNodes(@NotNull TreePath parent) {
      expandPaths(collectCachedExpandedPaths(parent));
    }

    private @NotNull Iterable<TreePath> collectCachedExpandedPaths(@NotNull TreePath parent) {
      var model = getModel();
      if (model == null) return emptyList();
      return cachedTree.getExpandedDescendants(model, parent);
    }
  }

  private class ExpandImpl implements CachedTreePresentationSupport {

    private final Map<TreePath, Boolean> expandedState = new HashMap<>();

    private @Nullable CachedPresentationImpl cachedPresentation;

    private ExpandImpl() {
      var rootPath = getRootPath();
      if (rootPath != null) {
        // Additionally, expand everything that was already expanded by base class constructors.
        var toggled = Tree.super.getDescendantToggledPaths(rootPath);
        if (toggled != null) {
          while (toggled.hasMoreElements()) {
            var toggledPath = toggled.nextElement();
            // Put it regardless of whether it's true or false, because a non-null value is needed for hasBeenExpanded().
            expandedState.put(toggledPath, Tree.super.isExpanded(toggledPath));
          }
        }
      }
      // Clean up whatever mess the super() constructor left in the superclass expandedState which we don't use.
      Tree.super.clearToggledPaths();
    }

    @Nullable CachedTreePresentation getCachedPresentation() {
      return cachedPresentation != null ? cachedPresentation.cachedTree : null;
    }

    @Override
    public void setCachedPresentation(@Nullable CachedTreePresentation presentation) {
      if (cachedPresentation != null && presentation != null) {
        // This can happen if the presentation is applied twice too quickly, before the previous one is cleared.
        // This causes glitches because the new presentation doesn't have the real-to-cache node mapping
        // for the nodes that have already been loaded.
        // We could try copying this cache to the new instance here, but it's error-prone and not really necessary
        // because it's most likely that the new presentation is the same as the previous one.
        return;
      }
      cachedPresentation = presentation == null ? null : new CachedPresentationImpl(presentation);
      if (cachedPresentation != null) {
        var rootPath = getRootPath();
        if (rootPath != null) {
          cachedPresentation.updateExpandedNodes(rootPath);
        }
      }
      // The order is important here because the model may immediately fire an event
      // that must be handled by expandImpl, so the model has to be updated last.
      if (getModel() instanceof CachedTreePresentationSupport cps) {
        cps.setCachedPresentation(presentation);
      }
    }

    void markPathExpanded(@NotNull TreePath path) {
      markPathExpandedState(path, true);
    }

    void markPathCollapsed(TreePath path) {
      markPathExpandedState(path, false);
    }

    private void markPathExpandedState(@NotNull TreePath path, boolean expanded) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(new Throwable((expanded ? "Expanding" : "Collapsing") + " " + path));
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug((expanded ? "Expanding" : "Collapsing") + " " + path);
      }
      var model = getModel();
      if (!expanded && model != null && model.isLeaf(path.getLastPathComponent())) {
        expandedState.remove(path); // save memory on leafs
      }
      else {
        expandedState.put(path, expanded);
      }
      if (cachedPresentation != null) {
        cachedPresentation.setExpanded(path, expanded);
      }
      // If the change came from the view model, we shouldn't translate it back to the view model,
      // because the change might not be the latest.
      // E.g., we received expanded=true, but there's another expanded=false update pending.
      // If we translate expanded=true back to the model, it'll overwrite that expanded=false (which is a newer state!).
      if (path.getLastPathComponent() instanceof TreeNodeViewModel viewModel) {
        if (applyingViewModelChanges.get()) {
          LOG.debug("Not forwarding the new state to the view model because it came from the model itself");
        }
        else {
          LOG.debug("Forwarding the new state to the view model");
          viewModel.setExpanded(expanded);
        }
      }
    }

    @NotNull Set<TreePath> getExpandedPaths() {
      var result = new HashSet<TreePath>();
      var rootPath = getRootPath();
      if (!isRootVisible() || isExpanded(rootPath)) {
        result.add(rootPath);
      }
      for (Map.Entry<TreePath, Boolean> e : expandedState.entrySet()) {
        if (e.getValue()) {
          result.add(e.getKey());
        }
      }
      return result;
    }

    @Nullable Enumeration<TreePath> getExpandedDescendants(@Nullable TreePath parent) {
      if (parent == null || !isExpanded(parent)) {
        return null;
      }
      Set<TreePath> toggledPaths = expandedState.keySet();
      List<TreePath> elements = null;
      for (var path : toggledPaths) {
        var value = expandedState.get(path);
        if (!path.equals(parent) && value != null && value.booleanValue() && parent.isDescendant(path) && isVisible(path)) {
          if (elements == null) {
            elements = new ArrayList<>();
          }
          elements.add(path);
        }
      }
      return elements == null ? Collections.emptyEnumeration() : Collections.enumeration(elements);
    }

    boolean hasBeenExpanded(@Nullable TreePath path) {
      return path != null && expandedState.get(path) != null;
    }

    boolean isExpanded(@Nullable TreePath path) {
      if (path == null) {
        return false;
      }
      do {
        var value = expandedState.get(path);
        if (value == null || !value.booleanValue())
          return false;
      } while ((path = path.getParentPath()) != null);
      return true;
    }

    boolean isExpanded(int row) {
      TreeUI tree = getUI();
      if (tree != null) {
        TreePath path = tree.getPathForRow(Tree.this, row);
        if (path != null) {
          Boolean value = expandedState.get(path);
          return value != null && value.booleanValue();
        }
      }
      return false;
    }

    void expandPaths(@NotNull Iterable<@NotNull TreePath> paths) {
      var started = 0L;
      var count = 0L;
      if (LOG.isDebugEnabled()) {
        started = System.currentTimeMillis();
      }
      var pathList = toList(paths);
      if (pathList.size() == 1) {
        var path = pathList.get(0);
        if (isNotLeaf(path)) {
          setExpandedState(path, true);
        }
        return;
      }
      pathList.sort(Comparator.comparing(TreePath::getPathCount));
      Set<TreePath> toExpand = new LinkedHashSet<>();
      Set<TreePath> toNotExpand = new HashSet<>();
      for (TreePath path : pathList) {
        ++count;
        shouldAllParentsBeExpanded(path, toExpand, toNotExpand);
      }
      Set<TreePath> expandRoots = new LinkedHashSet<>();
      try {
        beginBulkOperation();
        fireBulkExpandStarted();
        suspendExpandCollapseAccessibilityAnnouncements();
        for (TreePath path : toExpand) {
          if (isNotLeaf(path)) {
            markPathExpanded(path);
            fireTreeExpanded(path);
            var parent = path.getParentPath();
            // Limit expanded roots to 5 to prevent too many announcements and performance issues caused by it.
            if (expandRoots.size() < 5 && (parent == null || !toExpand.contains(parent))) {
              expandRoots.add(path);
            }
          }
        }
      }
      finally {
        resumeExpandCollapseAccessibilityAnnouncements();
        fireBulkExpandEnded();
        endBulkOperation();
      }
      if (accessibleContext != null) {
        // Only announce the topmost expanded nodes, to avoid spamming announcements.
        for (TreePath expandRoot : expandRoots) {
          fireAccessibleTreeExpanded(expandRoot);
        }
        ((AccessibleJTree)accessibleContext).fireVisibleDataPropertyChange();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Expanded " + count + " paths, time: " + (System.currentTimeMillis() - started) + " ms");
      }
    }

    private boolean isNotLeaf(@NotNull TreePath path) {
      var model = getModel();
      return model != null && !model.isLeaf(path.getLastPathComponent());
    }

    private void beginBulkOperation() {
      bulkOperationsInProgress.incrementAndGet();
      var ui = getUI();
      if (ui instanceof TreeUiBulkExpandCollapseSupport bulk) {
        bulk.beginBulkOperation();
      }
    }

    private void endBulkOperation() {
      var ui = getUI();
      if (ui instanceof TreeUiBulkExpandCollapseSupport bulk) {
        bulk.endBulkOperation();
      }
      bulkOperationsInProgress.decrementAndGet();
    }

    void collapsePaths(@NotNull Iterable<@NotNull TreePath> paths) {
      var started = 0L;
      var count = 0L;
      if (LOG.isDebugEnabled()) {
        started = System.currentTimeMillis();
      }
      var pathList = toList(paths);
      if (pathList.size() == 1) {
        var path = pathList.get(0);
        if (isNotLeaf(path)) {
          setExpandedState(path, false);
        }
        else { // the path has become a leaf, remove it to save memory
          expandedState.remove(path);
        }
        return;
      }
      pathList.sort(Comparator.comparing(TreePath::getPathCount));
      Set<TreePath> toExpand = new LinkedHashSet<>();
      Set<TreePath> toNotExpand = new HashSet<>();
      Set<TreePath> toCollapse = new LinkedHashSet<>();
      Set<TreePath> collapseRoots = new LinkedHashSet<>();
      for (TreePath path : pathList) {
        ++count;
        TreePath parent = path.getParentPath();
        boolean parentWillBeCollapsed = toCollapse.contains(parent);
        boolean pathWillBeCollapsed = false;
        if (parent == null || toExpand.contains(parent) || parentWillBeCollapsed) {
          toCollapse.add(path);
          pathWillBeCollapsed = true;
        }
        else if (!toNotExpand.contains(parent)) {
          if (shouldAllParentsBeExpanded(parent, toExpand, toNotExpand)) {
            toCollapse.add(path);
            pathWillBeCollapsed = true;
          }
        }
        if (!parentWillBeCollapsed && pathWillBeCollapsed) {
          collapseRoots.add(path);
        }
      }
      List<TreePath> toCollapseList = new ArrayList<>(toCollapse);
      Collections.reverse(toCollapseList);
      try {
        beginBulkOperation();
        fireBulkCollapseStarted();
        suspendExpandCollapseAccessibilityAnnouncements();
        for (TreePath path : toExpand) {
          markPathExpanded(path);
          fireTreeExpanded(path);
        }
        for (TreePath path : toCollapseList) {
          if (isNotLeaf(path)) {
            markPathCollapsed(path);
            fireTreeCollapsed(path);
            if (removeDescendantSelectedPaths(path, false) && !isPathSelected(path)) {
              addSelectionPath(path);
            }
          }
          else { // the path has become a leaf, remove it to save memory
            expandedState.remove(path);
          }
        }
      }
      finally {
        resumeExpandCollapseAccessibilityAnnouncements();
        fireBulkCollapseEnded();
        endBulkOperation();
      }
      if (accessibleContext != null) {
        // Only announce the topmost collapsed nodes, to avoid spamming announcements.
        for (TreePath collapseRoot : collapseRoots) {
          fireAccessibleTreeCollapsed(collapseRoot);
        }
        ((AccessibleJTree)accessibleContext).fireVisibleDataPropertyChange();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Collapsed " + count + " paths, time: " + (System.currentTimeMillis() - started) + " ms");
      }
    }

    private static @NotNull ArrayList<TreePath> toList(@NotNull Iterable<@NotNull TreePath> paths) {
      ArrayList<TreePath> pathList;
      if (paths instanceof Collection<TreePath> pathCollection) {
        pathList = new ArrayList<>(pathCollection);
      }
      else {
        pathList = new ArrayList<>();
        paths.forEach(pathList::add);
      }
      return pathList;
    }

    private boolean shouldAllParentsBeExpanded(
      @NotNull TreePath path,
      @NotNull Set<@NotNull TreePath> toExpand,
      @NotNull Set<@NotNull TreePath> toNotExpand
    ) {
      Deque<TreePath> stack = null;
      TreePath parentPath = path;
      var result = true;
      while (parentPath != null) {
        if (isExpanded(parentPath) || toExpand.contains(parentPath)) {
          parentPath = null;
        }
        else if (toNotExpand.contains(parentPath)) {
          parentPath = null;
          result = false;
        }
        else {
          if (stack == null) {
            stack = new ArrayDeque<>();
          }
          stack.push(parentPath);
          parentPath = parentPath.getParentPath();
        }
      }
      while (stack != null && !stack.isEmpty()) {
        parentPath = stack.pop();
        if (result) {
          try {
            fireTreeWillExpand(parentPath);
          }
          catch (ExpandVetoException eve) {
            result = false;
          }
        }
        if (result) {
          toExpand.add(parentPath);
        }
        else {
          toNotExpand.add(parentPath);
        }
      }
      return result;
    }

    void setExpandedState(@Nullable TreePath path, boolean state) {
      if (path == null) {
        return;
      }
      if (!expandParentPaths(path)) {
        return;
      }
      if (state) {
        expandPath(path);
      }
      else {
        collapsePath(path);
      }
    }

    /**
     * Applies the expanded state from the view model
     * <p>
     *   Unlike the regular {@link #setExpandedState(TreePath, boolean)}, does not expand parent paths
     *   if the node is invisible.
     * </p>
     * @param path the path to the node (must be leading to a {@link TreeNodeViewModel})
     * @param state the new expanded state
     */
    void setExpandedStateFromViewModel(@NotNull TreePath path, boolean state) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting expanded state=" + state + " from the view model " + path);
      }
      if (isVisible(path)) {
        setExpandedState(path, state);
      }
      else {
        markPathExpandedState(path, state);
      }
    }

    private boolean expandParentPaths(@NotNull TreePath path) {
      Deque<TreePath> stack = null;
      TreePath parentPath = path.getParentPath();
      while (parentPath != null) {
        if (isExpanded(parentPath)) {
          parentPath = null;
        }
        else {
          if (stack == null) {
            stack = new ArrayDeque<>();
          }
          stack.push(parentPath);
          parentPath = parentPath.getParentPath();
        }
      }
      while (stack != null && !stack.isEmpty()) {
        parentPath = stack.pop();
        if (!isExpanded(parentPath)) {
          try {
            fireTreeWillExpand(parentPath);
          } catch (ExpandVetoException eve) {
            return false;
          }
          markPathExpanded(parentPath);
          fireTreeExpanded(parentPath);
          if (accessibleContext != null) {
            ((AccessibleJTree)accessibleContext).fireVisibleDataPropertyChange();
          }
        }
      }
      return true;
    }

    private void expandPath(@NotNull TreePath path) {
      if (Boolean.TRUE.equals(expandedState.get(path))) {
        return;
      }
      try {
        fireTreeWillExpand(path);
      }
      catch (ExpandVetoException eve) {
        return;
      }
      markPathExpanded(path);
      fireTreeExpanded(path);
      if (accessibleContext != null) {
        ((AccessibleJTree)accessibleContext).fireVisibleDataPropertyChange();
      }
    }

    private void collapsePath(@NotNull TreePath path) {
      if (!Boolean.TRUE.equals(expandedState.get(path))) {
        return;
      }
      try {
        fireTreeWillCollapse(path);
      }
      catch (ExpandVetoException eve) {
        return;
      }
      markPathCollapsed(path);
      fireTreeCollapsed(path);
      if (removeDescendantSelectedPaths(path, false) && !isPathSelected(path)) {
        addSelectionPath(path);
      }
      if (accessibleContext != null) {
        ((AccessibleJTree)accessibleContext).fireVisibleDataPropertyChange();
      }
    }

    @Nullable Enumeration<TreePath> getDescendantToggledPaths(@Nullable TreePath parent) {
      if (parent == null) {
        return null;
      }
      List<TreePath> descendants = new ArrayList<>();
      Set<TreePath> nodes = expandedState.keySet();
      for (var path : nodes) {
        if (parent.isDescendant(path)) {
          descendants.add(path);
        }
      }
      return Collections.enumeration(descendants);
    }

    void removeDescendantToggledPaths(Enumeration<TreePath> toRemove) {
      if (toRemove == null) {
        return;
      }
      while (toRemove.hasMoreElements()) {
        Enumeration<?> descendants = getDescendantToggledPaths(toRemove.nextElement());
        if (descendants != null) {
          while (descendants.hasMoreElements()) {
            expandedState.remove(descendants.nextElement());
          }
        }
      }
    }

    void clearToggledPaths() {
      expandedState.clear();
    }

    TreeModelListener createTreeModelListener() {
      return new TreeModelListenerImpl();
    }

    private class TreeModelListenerImpl implements TreeSwingModelListener {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        var path = e.getTreePath();
        if (path.getLastPathComponent() instanceof TreeNodeViewModel viewModel) {
          applyViewModelChange(() -> {
            expandImpl.setExpandedStateFromViewModel(path, viewModel.stateSnapshot().isExpanded());
          });
        }
      }

      private void applyViewModelChange(@NotNull Runnable runnable) {
        if (!applyingViewModelChanges.compareAndSet(false, true)) {
          throw new IllegalStateException("Already applying a view model change, changes should not be recursive, it's a bug");
        }
        try {
          runnable.run();
        }
        finally {
          applyingViewModelChanges.set(false);
        }
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        var model = getModel();
        var path = getPath(e);
        if (model == null || path == null) return;
        var parent = path.getLastPathComponent();
        var childCount = model.getChildCount(parent);
        for (int i : e.getChildIndices()) {
          if (i < 0 || i >= childCount) continue; // Sanity check. This actually happens with some models.
          var newChild = model.getChild(parent, i);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Inserted child " + i + " " + newChild + " of parent " + parent);
          }
          var childPath = path.pathByAddingChild(newChild);
          if (newChild instanceof TreeNodeViewModel) {
            applyViewModelChange(() -> {
              applyNewNodeExpandedState(model, childPath);
            });
          }
          // This has to be done AFTER applying the model's state,
          // as we should preserve the cached presentation's state to avoid
          // expand/collapse flickering.
          // So what happens is that we first apply the model's state (usually collapsed) to the tree
          // and then reset it right back in case it was expanded in the cached state.
          if (cachedPresentation != null) {
            cachedPresentation.updateExpandedNodes(childPath);
          }
        }
      }

      private void applyNewNodeExpandedState(@NotNull TreeModel model, @NotNull TreePath path) {
        var node = (TreeNodeViewModel)path.getLastPathComponent();
        var isExpanded = node.stateSnapshot().isExpanded();
        expandImpl.setExpandedStateFromViewModel(path, isExpanded);
        if (isExpanded) {
          var childCount = model.getChildCount(node);
          for (int i = 0; i < childCount; i++) {
            var child = model.getChild(node, i);
            if (child instanceof TreeNodeViewModel) {
              applyNewNodeExpandedState(model, path.pathByAddingChild(child));
            }
          }
        }
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        if (e == null) {
          return;
        }
        TreePath parent = getPath(e);
        if (parent == null) {
          return;
        }
        if (parent.getPathCount() == 1) {
          clearToggledPaths();
          Object treeRoot = treeModel.getRoot();
          if (treeRoot != null && !treeModel.isLeaf(treeRoot)) {
            markPathExpanded(parent);
          }
        }
        else if (expandedState.get(parent) != null) {
          List<TreePath> toRemove = new ArrayList<>(1);
          boolean isExpanded = isExpanded(parent);
          toRemove.add(parent);
          removeDescendantToggledPaths(Collections.enumeration(toRemove));
          if (isExpanded) {
            TreeModel model = getModel();
            if (model == null || model.isLeaf(parent.getLastPathComponent())) {
              collapsePath(parent);
            }
            else {
              markPathExpanded(parent);
            }
          }
        }
        Tree.this.removeDescendantSelectedPaths(parent, false);
        if (cachedPresentation != null) {
          cachedPresentation.updateExpandedNodes(parent);
        }
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        if (e == null) {
          return;
        }
        TreePath parent = getPath(e);
        Object[] children = e.getChildren();
        if (children == null || parent == null) {
          return;
        }
        TreePath path;
        List<TreePath> toRemove = new ArrayList<>(Math.max(1, children.length));
        for (int counter = children.length - 1; counter >= 0; counter--) {
          path = parent.pathByAddingChild(children[counter]);
          if (expandedState.get(path) != null) {
            toRemove.add(path);
          }
        }
        if (!toRemove.isEmpty()) {
          removeDescendantToggledPaths(Collections.enumeration(toRemove));
        }
        TreeModel model = getModel();
        if (model == null || model.isLeaf(parent.getLastPathComponent())) {
          expandedState.remove(parent);
        }
        removeDescendantSelectedPaths(e);
      }

      private void removeDescendantSelectedPaths(TreeModelEvent e) {
        TreePath pPath = getPath(e);
        if (pPath == null) return;
        Object[] oldChildren = e.getChildren();
        TreeSelectionModel sm = getSelectionModel();
        if (sm != null && oldChildren != null && oldChildren.length > 0) {
          for (int counter = oldChildren.length - 1; counter >= 0; counter--) {
            Tree.this.removeDescendantSelectedPaths(pPath.pathByAddingChild(oldChildren[counter]), true);
          }
        }
      }

      @Override
      public void selectionChanged(@NotNull TreeSwingModelSelectionEvent event) {
        applyViewModelChange(() -> {
          Tree.this.setSelectionPaths(event.getNewSelection());
        });
      }

      @Override
      public void scrollRequested(@NotNull TreeSwingModelScrollEvent event) {
        applyViewModelChange(() -> {
          TreeUtil.scrollToVisible(Tree.this, event.getScrollTo(), Registry.is("ide.tree.autoscrollToVCenter", false));
        });
      }
    }
  }

  private class MyUISettingsListener implements UISettingsListener {

    private boolean applyingUiSettings = false;
    private boolean toggleClickCountOverridden;
    private @Nullable MessageBusConnection connection;

    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      if (applyingUiSettings) {
        LOG.warn(new Throwable("Reentrant com.intellij.ui.treeStructure.Tree.MyUISettingsListener.uiSettingsChanged call"));
        return;
      }
      applyingUiSettings = true;
      try {
        // Only set it if the client has never called setToggleClickCount() directly. And if the setting is enabled, of course.
        if (!toggleClickCountOverridden && isExpandWithSingleClickSettingEnabled()) {
          setToggleClickCount(uiSettings.getExpandNodesWithSingleClick() ? 1 : 2);
        }
      }
      finally {
        applyingUiSettings = false;
      }
    }

    void setToggleClickCountCalled() {
      if (!applyingUiSettings) {
        toggleClickCountOverridden = true;
      }
    }

    void connect() {
      disconnect(); // not necessary, but just in case
      connection = ApplicationManager.getApplication().getMessageBus().connect();
      connection.subscribe(TOPIC, this);
      uiSettingsChanged(UISettings.getInstance());
    }

    void disconnect() {
      if (connection != null) {
        Disposer.dispose(connection);
        connection = null;
      }
    }
  }

  private class MyTreeModelListener implements TreeSwingModelListener {
    private final @NotNull LazyInitializer.LazyValue<@NotNull TreeModelListener> delegate = LazyInitializer.create(() -> {
      return expandImpl.createTreeModelListener();
    });

    private @Nullable TreeModelListener delegate() {
      // There's little we can do if the model starts spamming events in the JTree constructor.
      // A well-behaved model should avoid that, but sometimes implementations like AsyncTreeModel
      // start to eagerly evaluate something they can evaluate right away.
      // Our best bet is to simply initialize the right state later in the ExpandImpl constructor.
      return initialized ? delegate.get() : null;
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {
      var delegate = delegate();
      if (delegate != null) {
        delegate.treeNodesChanged(e);
      }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      var delegate = delegate();
      if (delegate != null) {
        delegate.treeNodesInserted(e);
      }
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
      var delegate = delegate();
      if (delegate != null) {
        delegate.treeNodesRemoved(e);
      }
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
      var delegate = delegate();
      if (delegate != null) {
        delegate.treeStructureChanged(e);
      }
    }

    @Override
    public void selectionChanged(@NotNull TreeSwingModelSelectionEvent event) {
      if (delegate() instanceof TreeSwingModelListener treeSwingModelListener) {
        treeSwingModelListener.selectionChanged(event);
      }
    }

    @Override
    public void scrollRequested(@NotNull TreeSwingModelScrollEvent event) {
      if (delegate() instanceof TreeSwingModelListener treeSwingModelListener) {
        treeSwingModelListener.scrollRequested(event);
      }
    }
  }
}
