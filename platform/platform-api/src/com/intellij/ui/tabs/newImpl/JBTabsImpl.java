// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.rd.RdIdeaKt;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.newImpl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowPassInfo;
import com.intellij.ui.tabs.newImpl.table.TableLayout;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.LazyUiDisposable;
import com.jetbrains.rd.util.lifetime.Lifetime;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.rd.DisposableExKt.createLifetime;
import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public class JBTabsImpl extends JComponent
  implements JBTabsEx, PropertyChangeListener, TimerListener, DataProvider, PopupMenuListener, Disposable, JBTabsPresentation, Queryable,
             UISettingsListener, QuickActionProvider, Accessible {

  @NonNls public static final Key<Integer> SIDE_TABS_SIZE_LIMIT_KEY = Key.create("SIDE_TABS_SIZE_LIMIT_KEY");
  static final int MIN_TAB_WIDTH = JBUIScale.scale(75);
  public static final int DEFAULT_MAX_TAB_WIDTH = JBUIScale.scale(300);

  public static final Color MAC_AQUA_BG_COLOR = Gray._200;
  private static final Comparator<TabInfo> ABC_COMPARATOR = (o1, o2) -> StringUtil.naturalCompare(o1.getText(), o2.getText());

  @NotNull final ActionManager myActionManager;
  private final List<TabInfo> myVisibleInfos = new ArrayList<>();
  private final Map<TabInfo, AccessibleTabPage> myInfo2Page = new HashMap<>();
  private final Map<TabInfo, Integer> myHiddenInfos = new HashMap<>();

  private TabInfo mySelectedInfo;
  public final Map<TabInfo, TabLabel> myInfo2Label = new HashMap<>();
  public final Map<TabInfo, Toolbar> myInfo2Toolbar = new HashMap<>();
  public Dimension myHeaderFitSize;

  private Insets myInnerInsets = JBUI.emptyInsets();

  private final List<EventListener> myTabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<TabsListener> myTabListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myFocused;

  private Getter<? extends ActionGroup> myPopupGroup;
  private String myPopupPlace;

  TabInfo myPopupInfo;
  final DefaultActionGroup myNavigationActions;

  final PopupMenuListener myPopupListener;
  JPopupMenu myActivePopup;

  public boolean myHorizontalSide = true;

  private boolean myStealthTabMode = false;

  private boolean mySideComponentOnTabs = true;

  private boolean mySideComponentBefore = true;

  private boolean mySizeBySelected;

  private DataProvider myDataProvider;

  private final WeakHashMap<Component, Component> myDeferredToRemove = new WeakHashMap<>();

  private SingleRowLayout mySingleRowLayout;
  private final TableLayout myTableLayout = new TableLayout(this);
  private final TabsSideSplitter mySplitter = new TabsSideSplitter(this);


  private TabLayout myLayout;
  private LayoutPassInfo myLastLayoutPass;
  private TabInfo myLastPaintedSelection;

  public boolean myForcedRelayout;

  private UiDecorator myUiDecorator;
  static final UiDecorator ourDefaultDecorator = new DefaultDecorator();

  private boolean myPaintFocus;

  private boolean myHideTabs = false;
  @Nullable private Project myProject;

  private boolean myRequestFocusOnLastFocusedComponent = false;
  private boolean myListenerAdded;
  final Set<TabInfo> myAttractions = new HashSet<>();
  private final Animator myAnimator;
  private List<TabInfo> myAllTabs;
  private IdeFocusManager myFocusManager;
  private static final boolean myAdjustBorders = true;

  boolean myAddNavigationGroup = true;

  private boolean myGhostsAlwaysVisible = false;
  private boolean myDisposed;
  private boolean myToDrawBorderIfTabsHidden = true;
  private Color myActiveTabFillIn;

  private boolean myTabLabelActionsAutoHide;

  private final TabActionsAutoHideListener myTabActionsAutoHideListener = new TabActionsAutoHideListener();
  private Disposable myTabActionsAutoHideListenerDisposable = Disposer.newDisposable();
  private IdeGlassPane myGlassPane;
  @NonNls private static final String LAYOUT_DONE = "Layout.done";
  @NonNls public static final String STRETCHED_BY_WIDTH = "Layout.stretchedByWidth";

  private TimedDeadzone.Length myTabActionsMouseDeadzone = TimedDeadzone.DEFAULT;

  private long myRemoveDeferredRequest;
  private boolean myTestMode;

  private JBTabsPosition myPosition = JBTabsPosition.top;

  private final JBTabsBorder myBorder = createTabBorder();
  private final BaseNavigationAction myNextAction;
  private final BaseNavigationAction myPrevAction;

  private boolean myTabDraggingEnabled;
  private DragHelper myDragHelper;
  private boolean myNavigationActionsEnabled = true;
  private boolean myUseBufferedPaint = true;

  protected TabInfo myDropInfo;
  private int myDropInfoIndex;
  protected boolean myShowDropLocation = true;

  private TabInfo myOldSelection;
  private SelectionChangeHandler mySelectionChangeHandler;

  private Runnable myDeferredFocusRequest;
  private boolean myAlwaysPaintSelectedTab;
  private int myFirstTabOffset;

  protected final JBTabPainter myTabPainter = createTabPainter();
  private boolean myAlphabeticalMode = false;
  private boolean mySupportsCompression = false;
  private String myEmptyText = null;
  private boolean myMouseInsideTabsArea = false;

  protected JBTabPainter createTabPainter() {
    return JBTabPainter.getDEFAULT();
  }

  protected JBTabsBorder createTabBorder() {
    return new JBDefaultTabsBorder(this);
  }

  public JBTabPainter getTabPainter() {
    return myTabPainter;
  }

  private Lifetime lifetime = createLifetime(this);
  private TabLabel tabLabelAtMouse;

  public JBTabsImpl(@NotNull Project project) {
    this(project, project);
  }

  private JBTabsImpl(@NotNull Project project, @NotNull Disposable parent) {
    this(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), parent);
  }

  public JBTabsImpl(@Nullable Project project, IdeFocusManager focusManager, @NotNull Disposable parent) {
    this(project, ActionManager.getInstance(), focusManager, parent);
  }

  public JBTabsImpl(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    myProject = project;
    myActionManager = actionManager;
    myFocusManager = focusManager != null ? focusManager : IdeFocusManager.getGlobalInstance();

    setOpaque(true);
    setBackground(myTabPainter.getBackgroundColor());

    setBorder(myBorder);

    Disposer.register(parent, this);

    myNavigationActions = new DefaultActionGroup();

    myNextAction = new SelectNextAction(this, myActionManager);
    myPrevAction = new SelectPreviousAction(this, myActionManager);

    myNavigationActions.add(myNextAction);
    myNavigationActions.add(myPrevAction);

    setUiDecorator(null);

    mySingleRowLayout = createSingleRowLayout();
    myLayout = mySingleRowLayout;

    if(JBTabsFactory.getUseNewTabs()) {
      OnePixelDivider divider = mySplitter.getDivider();
      divider.setOpaque(false);
    }

    myPopupListener = new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        disposePopupListener();
      }

      @Override
      public void popupMenuCanceled(final PopupMenuEvent e) {
        disposePopupListener();
      }
    };

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (mySingleRowLayout.myLastSingRowLayout != null &&
            mySingleRowLayout.myLastSingRowLayout.moreRect != null &&
            mySingleRowLayout.myLastSingRowLayout.moreRect.contains(e.getPoint())) {
          showMorePopup(e);
        }
      }
    });
    addMouseWheelListener(event -> {
      int units = event.getUnitsToScroll();

      // Workaround for 'shaking' scrolling with touchpad when some events have units with opposite (wrong) sign
      if (SystemInfo.isMac && event.getModifiers() == InputEvent.SHIFT_MASK) return;

      if (units == 0) return;
      if (mySingleRowLayout.myLastSingRowLayout != null) {
        mySingleRowLayout.scroll(units * mySingleRowLayout.getScrollUnitIncrement());
        revalidateAndRepaint(false);
      }
    });
    AWTEventListener listener = new AWTEventListener() {
      final Alarm afterScroll = new Alarm(com.intellij.ui.tabs.newImpl.JBTabsImpl.this);
      @Override
      public void eventDispatched(AWTEvent event) {
        if (mySingleRowLayout.myLastSingRowLayout == null) return;

        MouseEvent me = (MouseEvent)event;
        Point point = me.getPoint();
        SwingUtilities.convertPointToScreen(point, me.getComponent());
        Rectangle rect = com.intellij.ui.tabs.newImpl.JBTabsImpl.this.getVisibleRect();
        rect = rect.intersection(mySingleRowLayout.myLastSingRowLayout.tabRectangle);
        Point p = rect.getLocation();
        SwingUtilities.convertPointToScreen(p, com.intellij.ui.tabs.newImpl.JBTabsImpl.this);
        rect.setLocation(p);
        boolean inside = rect.contains(point);
        if (inside != myMouseInsideTabsArea) {
          myMouseInsideTabsArea = inside;
          afterScroll.cancelAllRequests();
          if (!inside) {
            afterScroll.addRequest(new Runnable() {
              @Override
              public void run() {
                if (!myMouseInsideTabsArea) {
                  doLayoutTwice();
                }
              }
            }, 500);
          }
        }
      }
    };
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, InputEvent.MOUSE_MOTION_EVENT_MASK);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (toolkit != null) {
          toolkit.removeAWTEventListener(listener);
        }
      }
    });


    myAnimator = new Animator("JBTabs Attractions", 2, 500, true) {
      @Override
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        repaintAttractions();
      }
    };

    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
      @Override
      public Component getDefaultComponent(final Container aContainer) {
        return getToFocus();
      }
    });

    add(mySingleRowLayout.myLeftGhost);
    add(mySingleRowLayout.myRightGhost);


    new LazyUiDisposable<JBTabsImpl>(parent, this, this) {
      @Override
      protected void initialize(@NotNull Disposable parent, @NotNull JBTabsImpl child, @Nullable Project project) {
        if (myProject == null && project != null) {
          myProject = project;
        }

        Disposer.register(child, myAnimator);
        Disposer.register(child, new Disposable() {
          @Override
          public void dispose() {
            removeTimerUpdate();
          }
        });

        if (!myTestMode) {
          final IdeGlassPane gp = IdeGlassPaneUtil.find(child);
          if (gp != null) {
            myTabActionsAutoHideListenerDisposable = Disposer.newDisposable("myTabActionsAutoHideListener");
            Disposer.register(child, myTabActionsAutoHideListenerDisposable);
            gp.addMouseMotionPreprocessor(myTabActionsAutoHideListener, myTabActionsAutoHideListenerDisposable);
            myGlassPane = gp;
          }

          UIUtil.addAwtListener(new AWTEventListener() {
            @Override
            public void eventDispatched(final AWTEvent event) {
              if (mySingleRowLayout.myMorePopup != null) return;
              processFocusChange();
            }
          }, AWTEvent.FOCUS_EVENT_MASK, child);

          myDragHelper = new DragHelper(child);
          myDragHelper.start();
        }

        if (myProject != null && myFocusManager == IdeFocusManager.getGlobalInstance()) {
          myFocusManager = IdeFocusManager.getInstance(myProject);
        }
      }
    };
    UIUtil.putClientProperty(
      this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, new Iterable<JComponent>() {
        @Override
        public Iterator<JComponent> iterator() {
          return JBIterable.from(getVisibleInfos()).filter(Conditions.not(Conditions.is(mySelectedInfo))).transform(
            info -> info.getComponent()).iterator();
        }
      });
  }
  public boolean isMouseInsideTabsArea() {
    return myMouseInsideTabsArea;
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    for (Map.Entry<TabInfo, TabLabel> entry : myInfo2Label.entrySet()) {
      entry.getKey().revalidate();
    }
    boolean oldHideTabsIfNeeded = mySingleRowLayout instanceof ScrollableSingleRowLayout;
    boolean newHideTabsIfNeeded = UISettings.getInstance().getHideTabsIfNeeded();
    if (oldHideTabsIfNeeded != newHideTabsIfNeeded) {
      updateRowLayout();
    }
  }

  private void updateRowLayout() {
    boolean wasSingleRow = isSingleRow();
    if (mySingleRowLayout != null) {
      remove(mySingleRowLayout.myLeftGhost);
      remove(mySingleRowLayout.myRightGhost);
    }
    mySingleRowLayout = createSingleRowLayout();
    if (wasSingleRow) {
      myLayout = mySingleRowLayout;
    }
    add(mySingleRowLayout.myLeftGhost);
    add(mySingleRowLayout.myRightGhost);
    relayout(true, true);
  }

  protected SingleRowLayout createSingleRowLayout() {
    return new SingleRowLayout(this);
  }


  @Override
  public JBTabs setNavigationActionBinding(String prevActionId, String nextActionId) {
    if (myNextAction != null) {
      myNextAction.reconnect(nextActionId);
    }
    if (myPrevAction != null) {
      myPrevAction.reconnect(prevActionId);
    }

    return this;
  }

  public void setHovered(TabLabel label) {
    TabLabel old = tabLabelAtMouse;
    tabLabelAtMouse = label;

    if(old != null) {
      old.repaint();
    }

    if(tabLabelAtMouse != null) {
      tabLabelAtMouse.repaint();
    }
  }

  public void unHover(TabLabel label) {
    if(tabLabelAtMouse == label) {
      tabLabelAtMouse = null;
      label.repaint();
    }
  }

  protected boolean isHoveredTab(TabLabel label) {
    return label != null && label == tabLabelAtMouse;
  }

  protected boolean isActiveTabs(TabInfo info) {
    return UIUtil.isFocusAncestor(this);
  }

  @Override
  public boolean isEditorTabs() {
    return false;
  }

  public boolean supportsCompression() {
    return mySupportsCompression;
  }

  @Override
  public JBTabs setNavigationActionsEnabled(boolean enabled) {
    myNavigationActionsEnabled = enabled;
    return this;
  }

  @Override
  public final boolean isDisposed() {
    return myDisposed;
  }

  public static Image getComponentImage(TabInfo info) {
    JComponent cmp = info.getComponent();

    BufferedImage img;
    if (cmp.isShowing()) {
      final int width = cmp.getWidth();
      final int height = cmp.getHeight();
      img = UIUtil.createImage(info.getComponent(), width > 0 ? width : 500, height > 0 ? height : 500, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      cmp.paint(g);
    }
    else {
      img = UIUtil.createImage(info.getComponent(), 500, 500, BufferedImage.TYPE_INT_ARGB);
    }
    return img;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    mySelectedInfo = null;
    myDeferredFocusRequest = null;
    resetTabsCache();
    myAttractions.clear();
    myVisibleInfos.clear();
    myUiDecorator = null;
    myActivePopup = null;
    myInfo2Label.clear();
    myInfo2Page.clear();
    myInfo2Toolbar.clear();
    myTabListeners.clear();
    myLastLayoutPass = null;
  }

  protected void resetTabsCache() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myAllTabs = null;
  }

  private void processFocusChange() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) {
      setFocused(false);
      return;
    }

    if (owner == this || SwingUtilities.isDescendingFrom(owner, this)) {
      setFocused(true);
    }
    else {
      setFocused(false);
    }
  }

  private void repaintAttractions() {
    boolean needsUpdate = false;
    for (TabInfo each : myVisibleInfos) {
      TabLabel eachLabel = myInfo2Label.get(each);
      needsUpdate |= eachLabel.repaintAttraction();
    }

    if (needsUpdate) {
      relayout(true, false);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    addTimerUpdate();

    if (myDeferredFocusRequest != null) {
      final Runnable request = myDeferredFocusRequest;
      myDeferredFocusRequest = null;

      request.run();
    }
  }

  @Override
  public void removeNotify() {
    try {
      super.removeNotify();
    }
    catch (Exception e) {
      GuiUtils.printDebugInfo(this);
    }

    setFocused(false);

    removeTimerUpdate();

    if (ScreenUtil.isStandardAddRemoveNotify(this) && myGlassPane != null) {
      Disposer.dispose(myTabActionsAutoHideListenerDisposable);
      myTabActionsAutoHideListenerDisposable = Disposer.newDisposable();
      myGlassPane = null;
    }
  }

  @Override
  public void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
  }

  private void addTimerUpdate() {
    if (!myListenerAdded) {
      myActionManager.addTimerListener(500, this);
      myListenerAdded = true;
    }
  }

  private void removeTimerUpdate() {
    if (myListenerAdded) {
      myActionManager.removeTimerListener(this);
      myListenerAdded = false;
    }
  }

  void setTestMode(final boolean testMode) {
    myTestMode = testMode;
  }

  public void layoutComp(SingleRowPassInfo data, int deltaX, int deltaY, int deltaWidth, int deltaHeight) {
    JComponent hToolbar = data.hToolbar.get();
    JComponent vToolbar = data.vToolbar.get();
    if (hToolbar != null) {
      final int toolbarHeight = hToolbar.getPreferredSize().height;
      final int hSeparatorHeight = toolbarHeight > 0 ? 1 : 0;
      final Rectangle compRect = layoutComp(deltaX, toolbarHeight + hSeparatorHeight + deltaY, data.comp.get(), deltaWidth, deltaHeight);
      layout(hToolbar, compRect.x, compRect.y - toolbarHeight - hSeparatorHeight, compRect.width, toolbarHeight);
    }
    else if (vToolbar != null) {
      final int toolbarWidth = vToolbar.getPreferredSize().width;
      final int vSeparatorWidth = toolbarWidth > 0 ? 1 : 0;
      if (mySideComponentBefore) {
        final Rectangle compRect = layoutComp(toolbarWidth + vSeparatorWidth + deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight);
        layout(vToolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
      }
      else {
        final Rectangle compRect = layoutComp(new Rectangle(deltaX, deltaY, getWidth() - toolbarWidth - vSeparatorWidth, getHeight()),
                                              data.comp.get(), deltaWidth, deltaHeight);
        layout(vToolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
      }
    }
    else {
      layoutComp(deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight);
    }
  }

  public boolean isDropTarget(TabInfo info) {
    return myDropInfo != null && myDropInfo == info;
  }

  protected void setDropInfoIndex(int dropInfoIndex) {
    myDropInfoIndex = dropInfoIndex;
  }

  public int getFirstTabOffset() {
    return myFirstTabOffset;
  }

  @Override
  public void setFirstTabOffset(int firstTabOffset) {
    myFirstTabOffset = firstTabOffset;
  }

  @Override
  public JBTabsPresentation setEmptyText(@Nullable String text) {
    myEmptyText = text;
    return this;
  }

  public int tabMSize() {
    return 20;
  }


  /**
   * TODO use {@link RdIdeaKt#childAtMouse(com.intellij.openapi.wm.IdeGlassPane, java.awt.Container)}
   */
  @Deprecated
  class TabActionsAutoHideListener extends MouseMotionAdapter implements Weighted {

    private TabLabel myCurrentOverLabel;
    private Point myLastOverPoint;

    @Override
    public double getWeight() {
      return 1;
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
      if (!myTabLabelActionsAutoHide) return;

      myLastOverPoint = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), JBTabsImpl.this);
      processMouseOver();
    }

    void processMouseOver() {
      if (!myTabLabelActionsAutoHide) return;

      if (myLastOverPoint == null) return;

      if (myLastOverPoint.x >= 0 && myLastOverPoint.x < getWidth() && myLastOverPoint.y > 0 && myLastOverPoint.y < getHeight()) {
        final TabLabel label = myInfo2Label.get(_findInfo(myLastOverPoint, true));
        if (label != null) {
          if (myCurrentOverLabel != null) {
            myCurrentOverLabel.toggleShowActions(false);
          }
          label.toggleShowActions(true);
          myCurrentOverLabel = label;
          return;
        }
      }

      if (myCurrentOverLabel != null) {
        myCurrentOverLabel.toggleShowActions(false);
        myCurrentOverLabel = null;
      }
    }
  }


  @Override
  public ModalityState getModalityState() {
    return ModalityState.stateForComponent(this);
  }

  @Override
  public void run() {
    updateTabActions(false);
  }

  @Override
  public void updateTabActions(final boolean validateNow) {
    final Ref<Boolean> changed = new Ref<>(Boolean.FALSE);
    for (final TabInfo eachInfo : myInfo2Label.keySet()) {
        final boolean changes = myInfo2Label.get(eachInfo).updateTabActions();
        changed.set(changed.get().booleanValue() || changes);
    }

    if (changed.get()) {
      revalidateAndRepaint();
    }
  }

  @Override
  public boolean canShowMorePopup() {
    final SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
    return lastLayout != null && lastLayout.moreRect != null;
  }

  @Override
  public void showMorePopup(@Nullable final MouseEvent e) {
    final SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
    if (lastLayout == null) {
      return;
    }
    mySingleRowLayout.myMorePopup = new JBPopupMenu();
    for (final TabInfo each : getVisibleInfos()) {
      if (!mySingleRowLayout.isTabHidden(each)) continue;
      final JBMenuItem item = new JBMenuItem(each.getText(), each.getIcon());
      item.setForeground(each.getDefaultForeground());
      item.setBackground(each.getTabColor());
      mySingleRowLayout.myMorePopup.add(item);
      item.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          select(each, true);
        }
      });
    }

    mySingleRowLayout.myMorePopup.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        mySingleRowLayout.myMorePopup = null;
      }

      @Override
      public void popupMenuCanceled(final PopupMenuEvent e) {
        mySingleRowLayout.myMorePopup = null;
      }
    });

    if (e != null) {
      mySingleRowLayout.myMorePopup.show(this, e.getX(), e.getY());
    }
    else {
      final Rectangle rect = lastLayout.moreRect;
      if (rect != null) {
        mySingleRowLayout.myMorePopup.show(this, rect.x, rect.y + rect.height);
      }
    }
  }


  @Nullable
  private JComponent getToFocus() {
    final TabInfo info = getSelectedInfo();

    if (info == null) return null;

    JComponent toFocus = null;

    if (isRequestFocusOnLastFocusedComponent() && info.getLastFocusOwner() != null && !isMyChildIsFocusedNow()) {
      toFocus = info.getLastFocusOwner();
    }

    if (toFocus == null && info.getPreferredFocusableComponent() == null) {
      return null;
    }


    if (toFocus == null) {
      toFocus = info.getPreferredFocusableComponent();
      final JComponent policyToFocus = myFocusManager.getFocusTargetFor(toFocus);
      if (policyToFocus != null) {
        toFocus = policyToFocus;
      }
    }

    return toFocus;
  }

  @Override
  public void requestFocus() {
    final JComponent toFocus = getToFocus();
    if (toFocus != null) {
      getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(toFocus, true));
    }
    else {
      getGlobalInstance().doWhenFocusSettlesDown(() -> super.requestFocus());
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    final JComponent toFocus = getToFocus();
    if (toFocus != null) {
      return toFocus.requestFocusInWindow();
    }
    else {
      return super.requestFocusInWindow();
    }
  }


  @Override
  @NotNull
  public TabInfo addTab(TabInfo info, int index) {
    return addTab(info, index, false, true);
  }

  @Override
  public TabInfo addTabSilently(TabInfo info, int index) {
    return addTab(info, index, false, false);
  }

  private TabInfo addTab(TabInfo info, int index, boolean isDropTarget, boolean fireEvents) {
    if (!isDropTarget && getTabs().contains(info)) {
      return getTabs().get(getTabs().indexOf(info));
    }

    info.getChangeSupport().addPropertyChangeListener(this);
    final TabLabel label = createTabLabel(info);
    Disposer.register(this, label);
    myInfo2Label.put(info, label);
    myInfo2Page.put(info, new AccessibleTabPage(info));

    if (!isDropTarget) {
      if (index < 0 || index > myVisibleInfos.size() - 1) {
        myVisibleInfos.add(info);
      }
      else {
        myVisibleInfos.add(index, info);
      }
    }

    resetTabsCache();


    updateText(info);
    updateIcon(info);
    updateSideComponent(info);
    updateTabActions(info);

    add(label);

    adjust(info);

    updateAll(false, false);

    if (info.isHidden()) {
      updateHiding();
    }

    if (!isDropTarget && fireEvents) {
      if (getTabCount() == 1) {
        fireBeforeSelectionChanged(null, info);
        fireSelectionChanged(null, info);
      }
    }

    revalidateAndRepaint(false);

    return info;
  }

  // Looks hacky but makes selected tab scrolled to visible area precisely
  private void doLayoutTwice() {
    ApplicationManager.getApplication().invokeLater(() -> {
      doLayout();
      ApplicationManager.getApplication().invokeLater(() -> {
        doLayout();
      });
    });
  }

  protected TabLabel createTabLabel(TabInfo info) {
    return new TabLabel(this, info);
  }

  @Override
  @NotNull
  public TabInfo addTab(TabInfo info) {
    return addTab(info, -1);
  }

  @Override
  public TabLabel getTabLabel(TabInfo info) {
    return myInfo2Label.get(info);
  }

  @Nullable
  public ActionGroup getPopupGroup() {
    return myPopupGroup != null ? myPopupGroup.get() : null;
  }

  public String getPopupPlace() {
    return myPopupPlace;
  }

  @Override
  @NotNull
  public JBTabs setPopupGroup(@NotNull final ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup) {
    return setPopupGroup(() -> popupGroup, place, addNavigationGroup);
  }

  @Override
  @NotNull
  public JBTabs setPopupGroup(@NotNull final Getter<? extends ActionGroup> popupGroup,
                              @NotNull final String place,
                              final boolean addNavigationGroup) {
    myPopupGroup = popupGroup;
    myPopupPlace = place;
    myAddNavigationGroup = addNavigationGroup;
    return this;
  }

  private void updateAll(final boolean forcedRelayout, final boolean now) {
    mySelectedInfo = getSelectedInfo();
    updateContainer(forcedRelayout, now);
    removeDeferred();
    updateListeners();
    updateTabActions(false);
    updateEnabling();
  }

  private boolean isMyChildIsFocusedNow() {
    final Component owner = getFocusOwner();
    if (owner == null) return false;


    if (mySelectedInfo != null) {
      if (!SwingUtilities.isDescendingFrom(owner, mySelectedInfo.getComponent())) return false;
    }

    return SwingUtilities.isDescendingFrom(owner, this);
  }

  @Nullable
  private static JComponent getFocusOwner() {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return (JComponent)(owner instanceof JComponent ? owner : null);
  }

  @Override
  @NotNull
  public ActionCallback select(@NotNull TabInfo info, boolean requestFocus) {
    return _setSelected(info, requestFocus);
  }

  @NotNull
  private ActionCallback _setSelected(final TabInfo info, final boolean requestFocus) {
    if (!isEnabled()) {
      return ActionCallback.REJECTED;
    }

    if (mySelectionChangeHandler != null) {
      return mySelectionChangeHandler.execute(info, requestFocus, new ActiveRunnable() {
        @NotNull
        @Override
        public ActionCallback run() {
          return executeSelectionChange(info, requestFocus);
        }
      });
    }
    else {
      return executeSelectionChange(info, requestFocus);
    }
  }

  @NotNull
  private ActionCallback executeSelectionChange(TabInfo info, boolean requestFocus) {
    if (mySelectedInfo != null && mySelectedInfo.equals(info)) {
      if (!requestFocus) {
        return ActionCallback.DONE;
      }
      else {
        Component owner = myFocusManager.getFocusOwner();
        JComponent c = info.getComponent();
        if (c != null && owner != null) {
          if (c == owner || SwingUtilities.isDescendingFrom(owner, c)) {
            return ActionCallback.DONE;
          }
        }
        return requestFocus(getToFocus());
      }
    }

    if (myRequestFocusOnLastFocusedComponent && mySelectedInfo != null) {
      if (isMyChildIsFocusedNow()) {
        mySelectedInfo.setLastFocusOwner(getFocusOwner());
      }
    }

    TabInfo oldInfo = mySelectedInfo;
    mySelectedInfo = info;
    final TabInfo newInfo = getSelectedInfo();

    TabLabel label = myInfo2Label.get(info);
    if(label != null) {
      setComponentZOrder(label, 0);
    }

    fireBeforeSelectionChanged(oldInfo, newInfo);

    updateContainer(false, true);

    fireSelectionChanged(oldInfo, newInfo);

    if (requestFocus) {
      final JComponent toFocus = getToFocus();
      if (myProject != null && toFocus != null) {
        final ActionCallback result = new ActionCallback();
        requestFocus(toFocus).doWhenProcessed(() -> {
          if (myDisposed) {
            result.setRejected();
          }
          else {
            removeDeferred().notifyWhenDone(result);
          }
        });
        return result;
      }
      else {
        requestFocus();
        return removeDeferred();
      }
    }
    else {
      return removeDeferred();
    }
  }

  private void fireBeforeSelectionChanged(@Nullable TabInfo oldInfo, TabInfo newInfo) {
    if (oldInfo != newInfo) {
      myOldSelection = oldInfo;
      try {
        for (TabsListener eachListener : myTabListeners) {
          eachListener.beforeSelectionChanged(oldInfo, newInfo);
        }
      }
      finally {
        myOldSelection = null;
      }
    }
  }

  private void fireSelectionChanged(@Nullable TabInfo oldInfo, TabInfo newInfo) {
    if (oldInfo != newInfo) {
      for (TabsListener eachListener : myTabListeners) {
        if (eachListener != null) {
          eachListener.selectionChanged(oldInfo, newInfo);
        }
      }
    }
  }

  void fireTabsMoved() {
    for (TabsListener eachListener : myTabListeners) {
      if (eachListener != null) {
        eachListener.tabsMoved();
      }
    }
  }


  void fireTabRemoved(@NotNull TabInfo info) {
    for (TabsListener eachListener : myTabListeners) {
      if (eachListener != null) {
        eachListener.tabRemoved(info);
      }
    }
  }

  @NotNull
  private ActionCallback requestFocus(final JComponent toFocus) {
    if (toFocus == null) return ActionCallback.DONE;

    if (myTestMode) {
      getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(toFocus, true));
      return ActionCallback.DONE;
    }


    if (isShowing()) {
      return myFocusManager.requestFocus(toFocus, true);
    } else {
      return ActionCallback.REJECTED;
    }
  }

  @NotNull
  private ActionCallback removeDeferred() {
    if (myDeferredToRemove.isEmpty()) {
      return ActionCallback.DONE;
    }
    final ActionCallback callback = new ActionCallback();

    final long executionRequest = ++myRemoveDeferredRequest;

    final Runnable onDone = () -> {
      if (myRemoveDeferredRequest == executionRequest) {
        removeDeferredNow();
      }

      callback.setDone();
    };

    myFocusManager.doWhenFocusSettlesDown(onDone);

    return callback;
  }

  private void queueForRemove(Component c) {
    if (c instanceof JComponent) {
      addToDeferredRemove(c);
    }
    else {
      remove(c);
    }
  }

  private void unqueueFromRemove(Component c) {
    myDeferredToRemove.remove(c);
  }

  private void removeDeferredNow() {
    for (Component each : myDeferredToRemove.keySet()) {
      if (each != null && each.getParent() == this) {
        remove(each);
      }
    }
    myDeferredToRemove.clear();
  }

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {
    final TabInfo tabInfo = (TabInfo)evt.getSource();
    if (TabInfo.ACTION_GROUP.equals(evt.getPropertyName())) {
      updateSideComponent(tabInfo);
      relayout(false, false);
    }
    else if (TabInfo.COMPONENT.equals(evt.getPropertyName())) {
      relayout(true, false);
    }
    else if (TabInfo.TEXT.equals(evt.getPropertyName())) {
      updateText(tabInfo);
    }
    else if (TabInfo.ICON.equals(evt.getPropertyName())) {
      updateIcon(tabInfo);
      doLayoutTwice();
    }
    else if (TabInfo.TAB_COLOR.equals(evt.getPropertyName())) {
      updateColor(tabInfo);
    }
    else if (TabInfo.ALERT_STATUS.equals(evt.getPropertyName())) {
      boolean start = ((Boolean)evt.getNewValue()).booleanValue();
      updateAttraction(tabInfo, start);
    }
    else if (TabInfo.TAB_ACTION_GROUP.equals(evt.getPropertyName())) {
      updateTabActions(tabInfo);
      relayout(false, false);
    }
    else if (TabInfo.HIDDEN.equals(evt.getPropertyName())) {
      updateHiding();
      relayout(false, false);
    }
    else if (TabInfo.ENABLED.equals(evt.getPropertyName())) {
      updateEnabling();
    }
  }

  private void updateEnabling() {
    final List<TabInfo> all = getTabs();
    for (TabInfo each : all) {
      final TabLabel eachLabel = myInfo2Label.get(each);
      eachLabel.setTabEnabled(each.isEnabled());
    }

    final TabInfo selected = getSelectedInfo();
    if (selected != null && !selected.isEnabled()) {
      final TabInfo toSelect = getToSelectOnRemoveOf(selected);
      if (toSelect != null) {
        select(toSelect, myFocusManager.getFocusedDescendantFor(this) != null);
      }
    }
  }

  private void updateHiding() {
    boolean update = false;

    Iterator<TabInfo> visible = myVisibleInfos.iterator();
    while (visible.hasNext()) {
      TabInfo each = visible.next();
      if (each.isHidden() && !myHiddenInfos.containsKey(each)) {
        myHiddenInfos.put(each, myVisibleInfos.indexOf(each));
        visible.remove();
        update = true;
      }
    }


    Iterator<TabInfo> hidden = myHiddenInfos.keySet().iterator();
    while (hidden.hasNext()) {
      TabInfo each = hidden.next();
      if (!each.isHidden() && myHiddenInfos.containsKey(each)) {
        myVisibleInfos.add(getIndexInVisibleArray(each), each);
        hidden.remove();
        update = true;
      }
    }


    if (update) {
      resetTabsCache();
      if (mySelectedInfo != null && myHiddenInfos.containsKey(mySelectedInfo)) {
        mySelectedInfo = getToSelectOnRemoveOf(mySelectedInfo);
      }
      updateAll(true, false);
    }
  }

  private int getIndexInVisibleArray(TabInfo each) {
    Integer index = myHiddenInfos.get(each);
    if (index == null) {
      index = Integer.valueOf(myVisibleInfos.size());
    }

    if (index > myVisibleInfos.size()) {
      index = myVisibleInfos.size();
    }

    if (index.intValue() < 0) {
      index = 0;
    }

    return index.intValue();
  }

  private void updateIcon(final TabInfo tabInfo) {
    myInfo2Label.get(tabInfo).setIcon(tabInfo.getIcon());
    revalidateAndRepaint();
  }

  private void updateColor(final TabInfo tabInfo) {
    revalidateAndRepaint();
  }

  public void revalidateAndRepaint() {
    revalidateAndRepaint(true);
  }

  void revalidateAndRepaint(final boolean layoutNow) {
    if (myVisibleInfos.isEmpty()) {
      setOpaque(false);
      final Component nonOpaque = UIUtil.findUltimateParent(this);
      if (nonOpaque != null && getParent() != null) {
        final Rectangle toRepaint = SwingUtilities.convertRectangle(getParent(), getBounds(), nonOpaque);
        nonOpaque.repaint(toRepaint.x, toRepaint.y, toRepaint.width, toRepaint.height);
      }
    }
    else {
      setOpaque(true);
    }

    if (layoutNow) {
      validate();
    }
    else {
      revalidate();
    }
    repaint();
  }


  private void updateAttraction(final TabInfo tabInfo, boolean start) {
    if (start) {
      myAttractions.add(tabInfo);
    }
    else {
      myAttractions.remove(tabInfo);
      tabInfo.setBlinkCount(0);
    }

    if (start && !myAnimator.isRunning()) {
      myAnimator.resume();
    }
    else if (!start && myAttractions.isEmpty()) {
      myAnimator.suspend();
      repaintAttractions();
    }
  }

  private void updateText(final TabInfo tabInfo) {
    final TabLabel label = myInfo2Label.get(tabInfo);
    label.setText(tabInfo.getColoredText());
    label.setToolTipText(tabInfo.getTooltipText());

    revalidateAndRepaint();
  }

  private void updateSideComponent(final TabInfo tabInfo) {
    final Toolbar old = myInfo2Toolbar.get(tabInfo);
    if (old != null) {
      remove(old);
    }

    final Toolbar toolbar = createToolbarComponent(tabInfo);
    myInfo2Toolbar.put(tabInfo, toolbar);
    add(toolbar);
  }

  private void updateTabActions(final TabInfo info) {
    myInfo2Label.get(info).setTabActions(info.getTabLabelActions());
  }

  @Override
  @Nullable
  public TabInfo getSelectedInfo() {
    if (myOldSelection != null) return myOldSelection;

    if (!myVisibleInfos.contains(mySelectedInfo)) {
      mySelectedInfo = null;
    }
    return mySelectedInfo != null ? mySelectedInfo : !myVisibleInfos.isEmpty() ? myVisibleInfos.get(0) : null;
  }

  @Override
  @Nullable
  public TabInfo getToSelectOnRemoveOf(TabInfo info) {
    if (!myVisibleInfos.contains(info)) return null;
    if (mySelectedInfo != info) return null;

    if (myVisibleInfos.size() == 1) return null;

    int index = getVisibleInfos().indexOf(info);

    TabInfo result = null;
    if (index > 0) {
      result = findEnabledBackward(index, false);
    }

    if (result == null) {
      result = findEnabledForward(index, false);
    }

    return result;
  }

  @Nullable
  protected TabInfo findEnabledForward(int from, boolean cycle) {
    if (from < 0) return null;
    int index = from;
    List<TabInfo> infos = getVisibleInfos();
    while (true) {
      index++;
      if (index == infos.size()) {
        if (!cycle) break;
        index = 0;
      }
      if (index == from) break;
      final TabInfo each = infos.get(index);
      if (each.isEnabled()) return each;
    }

    return null;
  }

  public boolean isAlphabeticalMode() {
    return myAlphabeticalMode;
  }

  @Nullable
  protected TabInfo findEnabledBackward(int from, boolean cycle) {
    if (from < 0) return null;
    int index = from;
    List<TabInfo> infos = getVisibleInfos();
    while (true) {
      index--;
      if (index == -1) {
        if (!cycle) break;
        index = infos.size() - 1;
      }
      if (index == from) break;
      final TabInfo each = infos.get(index);
      if (each.isEnabled()) return each;
    }

    return null;
  }

  protected Toolbar createToolbarComponent(final TabInfo tabInfo) {
    return new Toolbar(this, tabInfo);
  }

  @Override
  @NotNull
  public TabInfo getTabAt(final int tabIndex) {
    return getTabs().get(tabIndex);
  }

  @Override
  @NotNull
  public List<TabInfo> getTabs() {
    if (myAllTabs != null) return myAllTabs;

    ArrayList<TabInfo> result = new ArrayList<>(myVisibleInfos);

    for (TabInfo each : myHiddenInfos.keySet()) {
      result.add(getIndexInVisibleArray(each), each);
    }
    if (isAlphabeticalMode()) {
      Collections.sort(result, ABC_COMPARATOR);
    }

    myAllTabs = result;

    return result;
  }

  @Override
  public TabInfo getTargetInfo() {
    return myPopupInfo != null ? myPopupInfo : getSelectedInfo();
  }

  @Override
  public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
  }

  @Override
  public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
    resetPopup();
  }

  @Override
  public void popupMenuCanceled(final PopupMenuEvent e) {
    resetPopup();
  }

  private void resetPopup() {
//todo [kirillk] dirty hack, should rely on ActionManager to understand that menu item was either chosen on or cancelled
    SwingUtilities.invokeLater(() -> {
      // No need to reset popup info if a new popup has been already opened and myPopupInfo refers to the corresponding info.
      if (myActivePopup == null) {
        myPopupInfo = null;
      }
    });
  }

  @Override
  public void setPaintBlocked(boolean blocked, final boolean takeSnapshot) {
  }

  private void addToDeferredRemove(final Component c) {
    if (!myDeferredToRemove.containsKey(c)) {
      myDeferredToRemove.put(c, c);
    }
  }

  private boolean isToDrawBorderIfTabsHidden() {
    return myToDrawBorderIfTabsHidden;
  }

  @Override
  @NotNull
  public JBTabsPresentation setToDrawBorderIfTabsHidden(final boolean toDrawBorderIfTabsHidden) {
    myToDrawBorderIfTabsHidden = toDrawBorderIfTabsHidden;
    return this;
  }

  @Override
  @NotNull
  public JBTabs getJBTabs() {
    return this;
  }

  public static class Toolbar extends JPanel {
    private final JBTabsImpl myTabs;

    public Toolbar(JBTabsImpl tabs, TabInfo info) {
      myTabs = tabs;

      setLayout(new BorderLayout());

      final ActionGroup group = info.getGroup();
      final JComponent side = info.getSideComponent();

      if (group != null) {
        final String place = info.getPlace();
        ActionToolbar toolbar =
          myTabs.myActionManager.createActionToolbar(place != null ? place : "JBTabs", group, myTabs.myHorizontalSide);
        toolbar.setTargetComponent(info.getActionsContextComponent());
        final JComponent actionToolbar = toolbar.getComponent();
        add(actionToolbar, BorderLayout.CENTER);
      }

      if (side != null) {
        if (group != null) {
          add(side, BorderLayout.EAST);
        }
        else {
          add(side, BorderLayout.CENTER);
        }
      }
    }

    public boolean isEmpty() {
      return getComponentCount() == 0;
    }
  }


  @Override
  public void doLayout() {
    try {
      myHeaderFitSize = computeHeaderFitSize();
      final Collection<TabLabel> labels = myInfo2Label.values();
      for (TabLabel each : labels) {
        each.setTabActionsAutoHide(myTabLabelActionsAutoHide);
      }


      List<TabInfo> visible = new ArrayList<>(getVisibleInfos());

      if (myDropInfo != null && !visible.contains(myDropInfo) && myShowDropLocation) {
        if (getDropInfoIndex() >= 0 && getDropInfoIndex() < visible.size()) {
          visible.add(getDropInfoIndex(), myDropInfo);
        }
        else {
          visible.add(myDropInfo);
        }
      }

      if (isSingleRow()) {
        mySingleRowLayout.scrollSelectionInView();
        myLastLayoutPass = mySingleRowLayout.layoutSingleRow(visible);
        myTableLayout.myLastTableLayout = null;
        OnePixelDivider divider = mySplitter.getDivider();
        if (divider.getParent() == this) {
          int location = getTabsPosition() == JBTabsPosition.left
                         ? mySingleRowLayout.myLastSingRowLayout.tabRectangle.width
                         : getWidth() - mySingleRowLayout.myLastSingRowLayout.tabRectangle.width;
          divider.setBounds(location, 0, 1, getHeight());
        }
      }
      else {
        myLastLayoutPass = myTableLayout.layoutTable(visible);
        mySingleRowLayout.myLastSingRowLayout = null;
      }

      if (isStealthModeEffective() && !isHideTabs()) {
        final TabLabel label = getSelectedLabel();
        final Rectangle bounds = label.getBounds();
        final Insets insets = getLayoutInsets();
        layout(label, insets.left, bounds.y, getWidth() - insets.right - insets.left, bounds.height);
      }


      moveDraggedTabLabel();

      myTabActionsAutoHideListener.processMouseOver();
    }
    finally {
      myForcedRelayout = false;
    }

    applyResetComponents();
  }

  void moveDraggedTabLabel() {
    if (myDragHelper != null && myDragHelper.myDragRec != null) {
      final TabLabel selectedLabel = myInfo2Label.get(getSelectedInfo());
      if (selectedLabel != null) {
        final Rectangle bounds = selectedLabel.getBounds();
        if (isHorizontalTabs()) {
          selectedLabel.setBounds(myDragHelper.myDragRec.x, bounds.y, bounds.width, bounds.height);
        }
        else {
          selectedLabel.setBounds(bounds.x, myDragHelper.myDragRec.y, bounds.width, bounds.height);
        }
      }
    }
  }

  private Dimension computeHeaderFitSize() {
    final Max max = computeMaxSize();

    if (myPosition == JBTabsPosition.top || myPosition == JBTabsPosition.bottom) {
      return new Dimension(getSize().width, myHorizontalSide ? Math.max(max.myLabel.height, max.myToolbar.height) : max.myLabel.height);
    }
    else {
      return new Dimension(max.myLabel.width + (myHorizontalSide ? 0 : max.myToolbar.width), getSize().height);
    }
  }

  public Rectangle layoutComp(int componentX, int componentY, final JComponent comp, int deltaWidth, int deltaHeight) {
    return layoutComp(new Rectangle(componentX, componentY, getWidth(), getHeight()), comp, deltaWidth, deltaHeight);
  }

  public Rectangle layoutComp(final Rectangle bounds, final JComponent comp, int deltaWidth, int deltaHeight) {
    final Insets insets = getLayoutInsets();

    final Insets inner = getInnerInsets();

    int x = insets.left + bounds.x + inner.left;
    int y = insets.top + bounds.y + inner.top;
    int width = bounds.width - insets.left - insets.right - bounds.x - inner.left - inner.right;
    int height = bounds.height - insets.top - insets.bottom - bounds.y - inner.top - inner.bottom;

    final boolean noTabsVisible = isStealthModeEffective() || isHideTabs();

    if (!noTabsVisible) {
      width += deltaWidth;
      height += deltaHeight;
    }

    return layout(comp, x, y, width, height);
  }


  @Override
  public JBTabsPresentation setInnerInsets(final Insets innerInsets) {
    myInnerInsets = innerInsets;
    return this;
  }

  private Insets getInnerInsets() {
    return myInnerInsets;
  }

  public Insets getLayoutInsets() {
    return myBorder.getEffectiveBorder();
  }

  public int getToolbarInset() {
    return getArcSize() + 1;
  }

  public void resetLayout(boolean resetLabels) {
    if (resetLabels) {
      mySingleRowLayout.myLeftGhost.reset();
      mySingleRowLayout.myRightGhost.reset();
    }

    for (TabInfo each : myVisibleInfos) {
      reset(each, resetLabels);
    }

    if (myDropInfo != null) {
      reset(myDropInfo, resetLabels);
    }

    for (TabInfo each : myHiddenInfos.keySet()) {
      reset(each, resetLabels);
    }

    for (Component eachDeferred : myDeferredToRemove.keySet()) {
      resetLayout((JComponent)eachDeferred);
    }
  }

  private void reset(final TabInfo each, final boolean resetLabels) {
    final JComponent c = each.getComponent();
    if (c != null) {
      resetLayout(c);
    }

    resetLayout(myInfo2Toolbar.get(each));

    if (resetLabels) {
      resetLayout(myInfo2Label.get(each));
    }
  }


  private static int getArcSize() {
    return 4;
  }

  public int getGhostTabLength() {
    return 15;
  }

  protected JBTabsPosition getPosition() {
    return myPosition;
  }

  /**
   * @deprecated You should implement {@link JBTabsBorder} interface
   */
  @Deprecated
  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myVisibleInfos.isEmpty()) {
      if (myEmptyText != null) {
        UISettings.setupAntialiasing(g);
        UIUtil.drawCenteredString((Graphics2D)g, getBounds(), myEmptyText);
      }
      return;
    }

    myTabPainter.fillBackground((Graphics2D)g, new Rectangle(0, 0, getWidth(), getHeight()));
    myBorder.paintBorder(this, g, 0, 0, getWidth(), getHeight());

    if (!isStealthModeEffective() && !isHideTabs()) {
      myLastPaintedSelection = getSelectedInfo();
    }
  }

  protected TabLabel getSelectedLabel() {
    return myInfo2Label.get(getSelectedInfo());
  }

  protected List<TabInfo> getVisibleInfos() {
    if (!isAlphabeticalMode()) {
      return myVisibleInfos;
    } else {
      List<TabInfo> sortedCopy = new ArrayList<>(myVisibleInfos);
      Collections.sort(sortedCopy, ABC_COMPARATOR);
      return sortedCopy;
    }
  }

  protected LayoutPassInfo getLastLayoutPass() {
    return myLastLayoutPass;
  }

  public static int getSelectionTabVShift() {
    return 2;
  }

  protected boolean isStealthModeEffective() {
    return myStealthTabMode && getTabCount() == 1 &&
           (isSideComponentVertical() || !isSideComponentOnTabs()) &&
           getTabsPosition() == JBTabsPosition.top;
  }


  private boolean isNavigationVisible() {
    if (myStealthTabMode && getTabCount() == 1) return false;
    return !myVisibleInfos.isEmpty();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    mySingleRowLayout.myMoreIcon.paintIcon(this, g);
  }

  private Max computeMaxSize() {
    Max max = new Max();
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      max.myLabel.height = Math.max(max.myLabel.height, label.getPreferredSize().height);
      max.myLabel.width = Math.max(max.myLabel.width, label.getPreferredSize().width);
      final Toolbar toolbar = myInfo2Toolbar.get(eachInfo);
      if (myLayout.isSideComponentOnTabs() && toolbar != null && !toolbar.isEmpty()) {
        max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.getPreferredSize().height);
        max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.getPreferredSize().width);
      }
    }
    if (getTabsPosition() == JBTabsPosition.left || getTabsPosition() == JBTabsPosition.right) {
      if (mySplitter.getSideTabsLimit() > 0) {
        max.myLabel.width = Math.min(max.myLabel.width, mySplitter.getSideTabsLimit());
      }
    }

    max.myToolbar.height++;

    return max;
  }

  @Override
  public Dimension getMinimumSize() {
    if (mySizeBySelected) {
      return computeSizeBySelected(true);
    }

    return computeSize(component -> component.getMinimumSize(), 1);
  }

  @Override
  public Dimension getPreferredSize() {
    if (mySizeBySelected) {
      return computeSizeBySelected(false);
    }

    return computeSize(component -> component.getPreferredSize(), 3);
  }

  @NotNull
  private Dimension computeSizeBySelected(boolean minimum) {
    Dimension size = new Dimension();
    TabInfo tabInfo = getSelectedInfo();
    if (tabInfo == null && myVisibleInfos.size() > 0) {
      tabInfo = myVisibleInfos.get(0);
    }

    JComponent component = tabInfo == null ? null : tabInfo.getComponent();
    if (component != null) {
      Dimension tabSize = minimum ? component.getMinimumSize() : component.getPreferredSize();
      if (tabSize != null) {
        size.width = tabSize.width;
        size.height = tabSize.height;
      }
    }

    addHeaderSize(size, 3);
    return size;
  }

  private Dimension computeSize(Function<? super JComponent, ? extends Dimension> transform, int tabCount) {
    Dimension size = new Dimension();
    for (TabInfo each : myVisibleInfos) {
      final JComponent c = each.getComponent();
      if (c != null) {
        final Dimension eachSize = transform.fun(c);
        size.width = Math.max(eachSize.width, size.width);
        size.height = Math.max(eachSize.height, size.height);
      }
    }

    addHeaderSize(size, tabCount);
    return size;
  }

  private void addHeaderSize(Dimension size, final int tabsCount) {
    Dimension header = computeHeaderPreferredSize(tabsCount);

    final boolean horizontal = getTabsPosition() == JBTabsPosition.top || getTabsPosition() == JBTabsPosition.bottom;
    if (horizontal) {
      size.height += header.height;
      size.width = Math.max(size.width, header.width);
    }
    else {
      size.height += Math.max(size.height, header.height);
      size.width += header.width;
    }

    final Insets insets = getLayoutInsets();
    size.width += insets.left + insets.right + 1;
    size.height += insets.top + insets.bottom + 1;
  }

  private Dimension computeHeaderPreferredSize(int tabsCount) {
    final Iterator<TabInfo> infos = myInfo2Label.keySet().iterator();
    Dimension size = new Dimension();
    int currentTab = 0;

    final boolean horizontal = getTabsPosition() == JBTabsPosition.top || getTabsPosition() == JBTabsPosition.bottom;

    while (infos.hasNext()) {
      final boolean canGrow = currentTab < tabsCount;

      TabInfo eachInfo = infos.next();
      final TabLabel eachLabel = myInfo2Label.get(eachInfo);
      final Dimension eachPrefSize = eachLabel.getPreferredSize();
      if (horizontal) {
        if (canGrow) {
          size.width += eachPrefSize.width;
        }
        size.height = Math.max(size.height, eachPrefSize.height);
      }
      else {
        size.width = Math.max(size.width, eachPrefSize.width);
        if (canGrow) {
          size.height += eachPrefSize.height;
        }
      }

      currentTab++;
    }

    if (isSingleRow() && isGhostsAlwaysVisible()) {
      if (horizontal) {
        size.width += getGhostTabLength() * 2;
      }
      else {
        size.height += getGhostTabLength() * 2;
      }
    }

    if (horizontal) {
      size.height += myBorder.getThickness();
    }
    else {
      size.width += myBorder.getThickness();
    }

    return size;
  }

  @Override
  public int getTabCount() {
    return getTabs().size();
  }

  @Override
  @NotNull
  public JBTabsPresentation getPresentation() {
    return this;
  }

  @Override
  @NotNull
  public ActionCallback removeTab(final TabInfo info) {
    return removeTab(info, null, true);
  }

  @Override
  @NotNull
  public ActionCallback removeTab(final TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus) {
    return removeTab(info, forcedSelectionTransfer, transferFocus, false);
  }

  @NotNull
  private ActionCallback removeTab(TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus, boolean isDropTarget) {
    if (myPopupInfo == info) myPopupInfo = null;

    if (!isDropTarget) {
      if (info == null || !getTabs().contains(info)) return ActionCallback.DONE;
    }

    if (isDropTarget && myLastLayoutPass != null) {
      myLastLayoutPass.myVisibleInfos.remove(info);
    }

    final ActionCallback result = new ActionCallback();

    TabInfo toSelect;
    if (forcedSelectionTransfer == null) {
      toSelect = getToSelectOnRemoveOf(info);
    }
    else {
      assert myVisibleInfos.contains(forcedSelectionTransfer) : "Cannot find tab for selection transfer, tab=" + forcedSelectionTransfer;
      toSelect = forcedSelectionTransfer;
    }


    if (toSelect != null) {
      boolean clearSelection = info.equals(mySelectedInfo);
      processRemove(info, false);
      if (clearSelection) {
        mySelectedInfo = info;
      }
      _setSelected(toSelect, transferFocus).doWhenProcessed(() -> removeDeferred().notifyWhenDone(result));
    }
    else {
      processRemove(info, true);
      removeDeferred().notifyWhenDone(result);
    }

    if (myVisibleInfos.isEmpty()) {
      removeDeferredNow();
    }

    revalidateAndRepaint(true);

    fireTabRemoved(info);

    return result;
  }

  private void processRemove(final TabInfo info, boolean forcedNow) {
    remove(myInfo2Label.get(info));
    remove(myInfo2Toolbar.get(info));

    JComponent tabComponent = info.getComponent();

    if (!isToDeferRemoveForLater(tabComponent) || forcedNow) {
      remove(tabComponent);
    }
    else {
      queueForRemove(tabComponent);
    }

    TabLabel tabLabel = myInfo2Label.get(info);
    if (tabLabel != null) {
      Disposer.dispose(tabLabel);
    }

    myVisibleInfos.remove(info);
    myHiddenInfos.remove(info);
    myInfo2Label.remove(info);
    myInfo2Page.remove(info);
    myInfo2Toolbar.remove(info);
    resetTabsCache();

    updateAll(false, false);

    // avoid leaks
    myLastPaintedSelection = null;
  }

  @Nullable
  @Override
  public TabInfo findInfo(Component component) {
    for (TabInfo each : getTabs()) {
      if (each.getComponent() == component) return each;
    }

    return null;
  }

  @Override
  public TabInfo findInfo(MouseEvent event) {
    return findInfo(event, false);
  }

  @Nullable
  private TabInfo findInfo(final MouseEvent event, final boolean labelsOnly) {
    final Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), this);
    return _findInfo(point, labelsOnly);
  }

  @Override
  public TabInfo findInfo(final Object object) {
    for (int i = 0; i < getTabCount(); i++) {
      final TabInfo each = getTabAt(i);
      final Object eachObject = each.getObject();
      if (eachObject != null && eachObject.equals(object)) return each;
    }
    return null;
  }

  @Nullable
  private TabInfo _findInfo(final Point point, boolean labelsOnly) {
    Component component = findComponentAt(point);
    if (component == null) return null;
    while (component != this || component != null) {
      if (component instanceof TabLabel) {
        return ((TabLabel)component).getInfo();
      }
      if (!labelsOnly) {
        final TabInfo info = findInfo(component);
        if (info != null) return info;
      }
      if (component == null) break;
      component = component.getParent();
    }

    return null;
  }

  @Override
  public void removeAllTabs() {
    for (TabInfo each : getTabs()) {
      removeTab(each);
    }
  }


  private static class Max {
    final Dimension myLabel = new Dimension();
    final Dimension myToolbar = new Dimension();
  }

  private void updateContainer(boolean forced, final boolean layoutNow) {
    if (myProject != null && !myProject.isOpen()) return;
    for (TabInfo each : new ArrayList<>(myVisibleInfos)) {
      final JComponent eachComponent = each.getComponent();
      if (getSelectedInfo() == each && getSelectedInfo() != null) {
        unqueueFromRemove(eachComponent);

        final Container parent = eachComponent.getParent();
        if (parent != null && parent != this) {
          parent.remove(eachComponent);
        }

        if (eachComponent.getParent() == null) {
          add(eachComponent);
        }
      }
      else {
        if (eachComponent.getParent() == null) continue;
        if (isToDeferRemoveForLater(eachComponent)) {
          queueForRemove(eachComponent);
        }
        else {
          remove(eachComponent);
        }
      }
    }

    mySingleRowLayout.scrollSelectionInView();
    relayout(forced, layoutNow);
  }

  @Override
  protected void addImpl(final Component comp, final Object constraints, final int index) {
    unqueueFromRemove(comp);

    if (comp instanceof TabLabel) {
      ((TabLabel)comp).apply(myUiDecorator != null ? myUiDecorator.getDecoration() : ourDefaultDecorator.getDecoration());
    }

    super.addImpl(comp, constraints, index);
  }


  private static boolean isToDeferRemoveForLater(JComponent c) {
    return c.getRootPane() != null;
  }

  void relayout(boolean forced, final boolean layoutNow) {
    if (!myForcedRelayout) {
      myForcedRelayout = forced;
    }
    revalidateAndRepaint(layoutNow);
  }

  public int getBorderThickness() {
    return myBorder.getThickness();
  }

  @Override
  @NotNull
  public JBTabs addTabMouseListener(@NotNull MouseListener listener) {
    removeListeners();
    myTabMouseListeners.add(listener);
    addListeners();
    return this;
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  private void addListeners() {
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      for (EventListener eachListener : myTabMouseListeners) {
        if (eachListener instanceof MouseListener) {
          label.addMouseListener((MouseListener)eachListener);
        }
        else if (eachListener instanceof MouseMotionListener) {
          label.addMouseMotionListener((MouseMotionListener)eachListener);
        }
        else {
          assert false;
        }
      }
    }
  }

  private void removeListeners() {
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      for (EventListener eachListener : myTabMouseListeners) {
        if (eachListener instanceof MouseListener) {
          label.removeMouseListener((MouseListener)eachListener);
        }
        else if (eachListener instanceof MouseMotionListener) {
          label.removeMouseMotionListener((MouseMotionListener)eachListener);
        }
        else {
          assert false;
        }
      }
    }
  }

  private void updateListeners() {
    removeListeners();
    addListeners();
  }

  @Override
  public JBTabs addListener(@NotNull TabsListener listener) {
    myTabListeners.add(listener);
    return this;
  }

  @Override
  public JBTabs setSelectionChangeHandler(SelectionChangeHandler handler) {
    mySelectionChangeHandler = handler;
    return this;
  }

  public void setFocused(final boolean focused) {
    if (myFocused == focused) return;

    myFocused = focused;

    if (myPaintFocus) {
      repaint();
    }
  }

  @Override
  public int getIndexOf(@Nullable final TabInfo tabInfo) {
    return getVisibleInfos().indexOf(tabInfo);
  }

  @Override
  public boolean isHideTabs() {
    return myHideTabs;
  }

  @Override
  public void setHideTabs(final boolean hideTabs) {
    if (isHideTabs() == hideTabs) return;

    myHideTabs = hideTabs;

    relayout(true, false);
  }

  @Override
  public JBTabsPresentation setPaintBorder(int top, int left, int right, int bottom) {
    return this;
  }

  @Override
  public JBTabsPresentation setTabSidePaintBorder(int size) {
    return this;
  }

  static int getBorder(int size) {
    return size == -1 ? 1 : size;
  }

  private boolean isPaintFocus() {
    return myPaintFocus;
  }

  @Override
  @NotNull
  public JBTabsPresentation setActiveTabFillIn(@Nullable final Color color) {
    if (!isChanged(myActiveTabFillIn, color)) return this;

    myActiveTabFillIn = color;
    revalidateAndRepaint(false);
    return this;
  }

  private static boolean isChanged(Object oldObject, Object newObject) {
    return !Comparing.equal(oldObject, newObject);
  }

  @Override
  @NotNull
  public JBTabsPresentation setTabLabelActionsAutoHide(final boolean autoHide) {
    if (myTabLabelActionsAutoHide != autoHide) {
      myTabLabelActionsAutoHide = autoHide;
      revalidateAndRepaint(false);
    }
    return this;
  }

  @Nullable
  protected Color getActiveTabFillIn() {
    return myActiveTabFillIn;
  }

  @Override
  public JBTabsPresentation setFocusCycle(final boolean root) {
    setFocusCycleRoot(root);
    return this;
  }


  @Override
  public JBTabsPresentation setPaintFocus(final boolean paintFocus) {
    myPaintFocus = paintFocus;
    return this;
  }

  @Override
  public JBTabsPresentation setAlwaysPaintSelectedTab(final boolean paintSelected) {
    myAlwaysPaintSelectedTab = paintSelected;
    return this;
  }

  private abstract static class BaseNavigationAction extends AnAction {
    private final ShadowAction myShadow;
    @NotNull private final ActionManager myActionManager;
    private final JBTabsImpl myTabs;

    protected BaseNavigationAction(@NotNull String copyFromID, @NotNull JBTabsImpl tabs, @NotNull ActionManager mgr) {
      myActionManager = mgr;
      myTabs = tabs;
      myShadow = new ShadowAction(this, myActionManager.getAction(copyFromID), tabs, tabs);
      setEnabledInModalContext(true);
    }

    @Override
    public final void update(@NotNull final AnActionEvent e) {
      JBTabsImpl tabs = (JBTabsImpl)e.getData(NAVIGATION_ACTIONS_KEY);
      e.getPresentation().setVisible(tabs != null);
      if (tabs == null) return;

      tabs = findNavigatableTabs(tabs);
      e.getPresentation().setEnabled(tabs != null);
      if (tabs != null) {
        _update(e, tabs, tabs.getVisibleInfos().indexOf(tabs.getSelectedInfo()));
      }
    }

    @Nullable
    protected JBTabsImpl findNavigatableTabs(JBTabsImpl tabs) {
      // The debugger UI contains multiple nested JBTabsImpl, where the innermost JBTabsImpl has only one tab. In this case,
      // the action should target the outer JBTabsImpl.
      if (tabs == null || tabs != myTabs) {
        return null;
      }
      if (isNavigatable(tabs)) {
        return tabs;
      }
      Component c = tabs.getParent();
      while (c != null) {
        if (c instanceof JBTabsImpl && isNavigatable((JBTabsImpl)c)) {
          return (JBTabsImpl)c;
        }
        c = c.getParent();
      }
      return null;
    }

    private static boolean isNavigatable(JBTabsImpl tabs) {
      final int selectedIndex = tabs.getVisibleInfos().indexOf(tabs.getSelectedInfo());
      return tabs.isNavigationVisible() && selectedIndex >= 0 && tabs.myNavigationActionsEnabled;
    }

    public void reconnect(String actionId) {
      myShadow.reconnect(myActionManager.getAction(actionId));
    }

    protected abstract void _update(AnActionEvent e, final JBTabsImpl tabs, int selectedIndex);

    @Override
    public final void actionPerformed(@NotNull final AnActionEvent e) {
      JBTabsImpl tabs = (JBTabsImpl)e.getData(NAVIGATION_ACTIONS_KEY);
      tabs = findNavigatableTabs(tabs);
      if (tabs == null) return;

      final int index = tabs.getVisibleInfos().indexOf(tabs.getSelectedInfo());
      if (index == -1) return;
      _actionPerformed(e, tabs, index);
    }

    protected abstract void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex);
  }

  private static class SelectNextAction extends BaseNavigationAction {

    private SelectNextAction(JBTabsImpl tabs, @NotNull ActionManager mgr) {
      super(IdeActions.ACTION_NEXT_TAB, tabs, mgr);
    }

    @Override
    protected void _update(final AnActionEvent e, final JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledForward(selectedIndex, true) != null);
    }

    @Override
    protected void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex) {
      TabInfo tabInfo = tabs.findEnabledForward(selectedIndex, true);
      if (tabInfo != null) {
        tabs.select(tabInfo, true);
      }
    }
  }

  private static class SelectPreviousAction extends BaseNavigationAction {
    private SelectPreviousAction(JBTabsImpl tabs, @NotNull ActionManager mgr) {
      super(IdeActions.ACTION_PREVIOUS_TAB, tabs, mgr);
    }

    @Override
    protected void _update(final AnActionEvent e, final JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledBackward(selectedIndex, true) != null);
    }

    @Override
    protected void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex) {
      TabInfo tabInfo = tabs.findEnabledBackward(selectedIndex, true);
      if (tabInfo != null) {
        tabs.select(tabInfo, true);
      }
    }
  }

  private void disposePopupListener() {
    if (myActivePopup != null) {
      myActivePopup.removePopupMenuListener(myPopupListener);
      myActivePopup = null;
    }
  }

  @Override
  public JBTabsPresentation setStealthTabMode(final boolean stealthTabMode) {
    myStealthTabMode = stealthTabMode;

    relayout(true, false);

    return this;
  }

  public boolean isStealthTabMode() {
    return myStealthTabMode;
  }

  @Override
  public JBTabsPresentation setSideComponentVertical(final boolean vertical) {
    myHorizontalSide = !vertical;

    for (TabInfo each : myVisibleInfos) {
      each.getChangeSupport().firePropertyChange(TabInfo.ACTION_GROUP, "new1", "new2");
    }


    relayout(true, false);

    return this;
  }

  @Override
  public JBTabsPresentation setSideComponentOnTabs(boolean onTabs) {
    mySideComponentOnTabs = onTabs;

    relayout(true, false);

    return this;
  }

  @Override
  public JBTabsPresentation setSideComponentBefore(boolean before) {
    mySideComponentBefore = before;

    relayout(true, false);

    return this;
  }

  @Override
  public JBTabsPresentation setSingleRow(boolean singleRow) {
    myLayout = singleRow ? mySingleRowLayout : myTableLayout;

    relayout(true, false);

    return this;
  }

  @Override
  public JBTabsPresentation setGhostsAlwaysVisible(final boolean visible) {
    myGhostsAlwaysVisible = visible;

    relayout(true, false);

    return this;
  }

  public boolean isSizeBySelected() {
    return mySizeBySelected;
  }

  public void setSizeBySelected(boolean value) {
    mySizeBySelected = value;
  }

  public boolean useSmallLabels() {
    return false;
  }

  public boolean useBoldLabels() {
    return false;
  }

  public boolean isGhostsAlwaysVisible() {
    return myGhostsAlwaysVisible;
  }

  @Override
  public boolean isSingleRow() {
    return getEffectiveLayout() == mySingleRowLayout;
  }

  public boolean isSideComponentVertical() {
    return !myHorizontalSide;
  }

  public boolean isSideComponentOnTabs() {
    return mySideComponentOnTabs;
  }

  public boolean isSideComponentBefore() {
    return mySideComponentBefore;
  }

  public TabLayout getEffectiveLayout() {
    if (myLayout == myTableLayout && getTabsPosition() == JBTabsPosition.top) return myTableLayout;
    return mySingleRowLayout;
  }

  @Override
  public JBTabsPresentation setUiDecorator(@Nullable UiDecorator decorator) {
    myUiDecorator = decorator == null ? ourDefaultDecorator : decorator;
    applyDecoration();
    return this;
  }

  @Override
  protected void setUI(final ComponentUI newUI) {
    super.setUI(newUI);
    applyDecoration();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    SwingUtilities.invokeLater(() -> {
      applyDecoration();

      revalidateAndRepaint(false);
    });
  }

  private void applyDecoration() {
    if (myUiDecorator != null) {
      UiDecorator.UiDecoration uiDecoration = myUiDecorator.getDecoration();
      for (TabLabel each : myInfo2Label.values()) {
        each.apply(uiDecoration);
      }
    }


    for (TabInfo each : getTabs()) {
      adjust(each);
    }

    relayout(true, false);
  }

  private void adjust(final TabInfo each) {
    if (myAdjustBorders) {
      UIUtil.removeScrollBorder(each.getComponent());
    }
  }

  @Override
  public void sortTabs(Comparator<? super TabInfo> comparator) {
    Collections.sort(myVisibleInfos, comparator);

    relayout(true, false);
  }

  private boolean isRequestFocusOnLastFocusedComponent() {
    return myRequestFocusOnLastFocusedComponent;
  }

  @Override
  public JBTabsPresentation setRequestFocusOnLastFocusedComponent(final boolean requestFocusOnLastFocusedComponent) {
    myRequestFocusOnLastFocusedComponent = requestFocusOnLastFocusedComponent;
    return this;
  }


  @Override
  @Nullable
  public Object getData(@NotNull @NonNls final String dataId) {
    if (myDataProvider != null) {
      final Object value = myDataProvider.getData(dataId);
      if (value != null) return value;
    }

    if (QuickActionProvider.KEY.getName().equals(dataId)) {
      return this;
    }

    return NAVIGATION_ACTIONS_KEY.is(dataId) ? this : null;
  }

  @NotNull
  @Override
  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<>();

    TabInfo selection = getSelectedInfo();
    if (selection != null) {
      ActionGroup group = selection.getGroup();
      if (group != null) {
        AnAction[] children = group.getChildren(null);
        Collections.addAll(result, children);
      }
    }

    return result;
  }

  @Override
  public DataProvider getDataProvider() {
    return myDataProvider;
  }

  @Override
  public JBTabsImpl setDataProvider(@NotNull final DataProvider dataProvider) {
    myDataProvider = dataProvider;
    return this;
  }


  public static boolean isSelectionClick(final MouseEvent e, boolean canBeQuick) {
    if (e.getClickCount() == 1 || canBeQuick) {
      if (!e.isPopupTrigger()) {
        return e.getButton() == MouseEvent.BUTTON1 && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown();
      }
    }

    return false;
  }


  private static class DefaultDecorator implements UiDecorator {
    @Override
    @NotNull
    public UiDecoration getDecoration() {
        return new UiDecoration(null, new JBInsets(6, 12, 8, 12));
    }
  }

  public Rectangle layout(JComponent c, Rectangle bounds) {
    final Rectangle now = c.getBounds();
    if (!bounds.equals(now)) {
      c.setBounds(bounds);
    }
    c.putClientProperty(LAYOUT_DONE, Boolean.TRUE);

    return bounds;
  }

  public Rectangle layout(JComponent c, int x, int y, int width, int height) {
    return layout(c, new Rectangle(x, y, width, height));
  }

  public static void resetLayout(JComponent c) {
    if (c == null) return;
    c.putClientProperty(LAYOUT_DONE, null);
    c.putClientProperty(STRETCHED_BY_WIDTH, null);
  }

  private void applyResetComponents() {
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      if (each instanceof JComponent) {
        final JComponent jc = (JComponent)each;
        if (!UIUtil.isClientPropertyTrue(jc, LAYOUT_DONE)) {
          layout(jc, new Rectangle(0, 0, 0, 0));
        }
      }
    }
  }


  @Override
  @NotNull
  public JBTabsPresentation setTabLabelActionsMouseDeadzone(final TimedDeadzone.Length length) {
    myTabActionsMouseDeadzone = length;
    final List<TabInfo> all = getTabs();
    for (TabInfo each : all) {
      final TabLabel eachLabel = myInfo2Label.get(each);
      eachLabel.updateTabActions();
    }
    return this;
  }

  @Override
  @NotNull
  public JBTabsPresentation setTabsPosition(final JBTabsPosition position) {
    myPosition = position;
    OnePixelDivider divider = mySplitter.getDivider();
    if ((position == JBTabsPosition.left || position == JBTabsPosition.right) && divider.getParent() == null) {
      add(divider);
    } else if (divider.getParent() == this){
      remove(divider);
    }
    relayout(true, false);
    return this;
  }

  @Override
  public JBTabsPosition getTabsPosition() {
    return myPosition;
  }

  public TimedDeadzone.Length getTabActionsMouseDeadzone() {
    return myTabActionsMouseDeadzone;
  }

  @Override
  public JBTabsPresentation setTabDraggingEnabled(boolean enabled) {
    myTabDraggingEnabled = enabled;
    return this;
  }

  @Override
  public JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode) {
    myAlphabeticalMode = alphabeticalMode;
    return this;
  }

  @Override
  public JBTabsPresentation setSupportsCompression(boolean supportsCompression) {
    mySupportsCompression = supportsCompression;
    updateRowLayout();
    return this;
  }

  public boolean isTabDraggingEnabled() {
    return myTabDraggingEnabled && !mySplitter.isDragging();
  }

  void reallocate(TabInfo source, TabInfo target) {
    if (source == target || source == null || target == null) return;

    final int targetIndex = myVisibleInfos.indexOf(target);

    myVisibleInfos.remove(source);
    myVisibleInfos.add(targetIndex, source);

    invalidate();
    relayout(true, true);
  }

  public boolean isHorizontalTabs() {
    return getTabsPosition() == JBTabsPosition.top || getTabsPosition() == JBTabsPosition.bottom;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    final TabInfo selected = getSelectedInfo();
    if (selected != null) {
      selected.putInfo(info);
    }
  }


  @Override
  public void resetDropOver(TabInfo tabInfo) {
    if (myDropInfo != null) {
      TabInfo dropInfo = myDropInfo;
      myDropInfo = null;
      myShowDropLocation = true;
      myForcedRelayout = true;
      setDropInfoIndex(-1);
      if (!isDisposed()) {
        removeTab(dropInfo, null, false, true);
      }
    }
  }

  @Override
  public Image startDropOver(TabInfo tabInfo, RelativePoint point) {
    myDropInfo = tabInfo;

    int index = myLayout.getDropIndexFor(point.getPoint(this));
    setDropInfoIndex(index);
    addTab(myDropInfo, index, true, true);

    TabLabel label = myInfo2Label.get(myDropInfo);
    Dimension size = label.getPreferredSize();
    label.setBounds(0, 0, size.width, size.height);

    BufferedImage img = UIUtil.createImage(this, size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    label.paintOffscreen(g);
    g.dispose();

    relayout(true, false);

    return img;
  }

  @Override
  public void processDropOver(TabInfo over, RelativePoint point) {
    int index = myLayout.getDropIndexFor(point.getPoint(this));
    if (index != getDropInfoIndex()) {
      setDropInfoIndex(index);
      relayout(true, false);
    }
  }

  @Override
  public int getDropInfoIndex() {
    return myDropInfoIndex;
  }

  @Override
  public boolean isEmptyVisible() {
    return myVisibleInfos.isEmpty();
  }

  public int getTabHGap() {
    return 0;
  }

  @Override
  public String toString() {
    return "JBTabs visible=" + myVisibleInfos + " selected=" + mySelectedInfo;
  }


  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJBTabsImpl();
    }
    return accessibleContext;
  }

  /**
   * Custom implementation of Accessible interface.  Given JBTabsImpl is similar
   * to the built-it JTabbedPane, we expose similar behavior. The one tricky part
   * is that JBTabsImpl can only expose the content of the selected tab, as the
   * content of tabs is created/deleted on demand when a tab is selected.
   */
  protected class AccessibleJBTabsImpl extends AccessibleJComponent implements AccessibleSelection {

    public AccessibleJBTabsImpl() {
      super();
      getAccessibleComponent();
      JBTabsImpl.this.addListener(new TabsListener() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          firePropertyChange(AccessibleContext.ACCESSIBLE_SELECTION_PROPERTY, null, null);
        }
      });
    }

    @Override
    public String getAccessibleName() {
      String name = accessibleName;
      if (name == null) {
        name = (String)getClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
      }

      if (name == null) {
        // Similar to JTabbedPane, we return the name of our selected tab
        // as our own name.
        TabLabel selectedLabel = getSelectedLabel();
        if (selectedLabel != null) {
          if (selectedLabel.getAccessibleContext() != null) {
            name = selectedLabel.getAccessibleContext().getAccessibleName();
          }
        }
      }

      if (name == null) {
        name = super.getAccessibleName();
      }
      return name;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PAGE_TAB_LIST;
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      Accessible accessibleChild = super.getAccessibleChild(i);
      // Note: Unlike a JTabbedPane, JBTabsImpl has many more child types than just pages.
      // So we wrap TabLabel instances with their corresponding AccessibleTabPage, while
      // leaving other types of children untouched.
      if (accessibleChild instanceof TabLabel) {
        TabLabel label = (TabLabel)accessibleChild;
        return myInfo2Page.get(label.getInfo());
      }
      return accessibleChild;
    }

    @Override
    public AccessibleSelection getAccessibleSelection() {
      return this;
    }

    @Override
    public int getAccessibleSelectionCount() {
      return (getSelectedInfo() == null ? 0 : 1);
    }

    @Override
    public Accessible getAccessibleSelection(int i) {
      if (getSelectedInfo() == null)
        return null;
      return myInfo2Page.get(getSelectedInfo());
    }

    @Override
    public boolean isAccessibleChildSelected(int i) {
      return (i == getIndexOf(getSelectedInfo()));
    }

    @Override
    public void addAccessibleSelection(int i) {
      TabInfo info = getTabAt(i);
      if (info != null) {
        select(info, false);
      }
    }

    @Override
    public void removeAccessibleSelection(int i) {
      // can't do
    }

    @Override
    public void clearAccessibleSelection() {
      // can't do
    }

    @Override
    public void selectAllAccessibleSelection() {
      // can't do
    }
  }

  /**
   * AccessibleContext implementation for a single tab page.
   *
   * A tab page has a label as the display zone, name, description, etc.
   * A tab page exposes a child component only if it corresponds to the
   * selected tab in the tab pane. Inactive tabs don't have a child
   * component to expose, as components are created/deleted on demand.
   * A tab page exposes one action: select and activate the panel.
   */
  private class AccessibleTabPage extends AccessibleContext
    implements Accessible, AccessibleComponent, AccessibleAction {

    private final @NotNull JBTabsImpl myParent;
    private final @NotNull TabInfo myTabInfo;
    private final Component myComponent;

    AccessibleTabPage(@NotNull TabInfo tabInfo) {
      myParent = JBTabsImpl.this;
      myTabInfo = tabInfo;
      myComponent = tabInfo.getComponent();
      setAccessibleParent(myParent);
      initAccessibleContext();
    }

    private @NotNull TabInfo getTabInfo() {
      return myTabInfo;
    }

    private int getTabIndex() {
      return JBTabsImpl.this.getIndexOf(myTabInfo);
    }

    private TabLabel getTabLabel() {
      return JBTabsImpl.this.myInfo2Label.get(getTabInfo());
    }

    /*
     * initializes the AccessibleContext for the page
     */
    void initAccessibleContext() {
      // Note: null checks because we do not want to load Accessibility classes unnecessarily.
      if (JBTabsImpl.this.accessibleContext != null && myComponent instanceof Accessible) {
        AccessibleContext ac;
        ac = myComponent.getAccessibleContext();
        if (ac != null) {
          ac.setAccessibleParent(this);
        }
      }
    }

    /////////////////
    // Accessibility support
    ////////////////

    @Override
    public AccessibleContext getAccessibleContext() {
      return this;
    }

    // AccessibleContext methods

    @Override
    public String getAccessibleName() {
      String name = accessibleName;
      if (name == null) {
        name = (String)getClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
      }

      if (name == null) {
        TabLabel label = getTabLabel();
        if (label != null && label.getAccessibleContext() != null) {
          name = label.getAccessibleContext().getAccessibleName();
        }
      }

      if (name == null) {
        name = super.getAccessibleName();
      }
      return name;
    }

    @Override
    public String getAccessibleDescription() {
      String description = accessibleDescription;
      if (description == null) {
        description = (String)getClientProperty(AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY);
      }

      if (description == null) {
        TabLabel label = getTabLabel();
        if (label != null && label.getAccessibleContext() != null) {
          description = label.getAccessibleContext().getAccessibleDescription();
        }
      }

      if (description == null) {
        description = super.getAccessibleDescription();
      }
      return description;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PAGE_TAB;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet states;
      states = myParent.getAccessibleContext().getAccessibleStateSet();
      states.add(AccessibleState.SELECTABLE);
      TabInfo info = myParent.getSelectedInfo();
      if (info == getTabInfo()) {
        states.add(AccessibleState.SELECTED);
      }
      return states;
    }

    @Override
    public int getAccessibleIndexInParent() {
      return getTabIndex();
    }

    @Override
    public int getAccessibleChildrenCount() {
      // Expose the tab content only if it is active, as the content for
      // inactive tab does is usually not ready (i.e. may never have been
      // activated).
      if (JBTabsImpl.this.getSelectedInfo() == getTabInfo() && myComponent instanceof Accessible) {
        return 1;
      } else {
        return 0;
      }
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      if (JBTabsImpl.this.getSelectedInfo() == getTabInfo() && myComponent instanceof Accessible) {
        return (Accessible) myComponent;
      } else {
        return null;
      }
    }

    @Override
    public Locale getLocale() {
      return JBTabsImpl.this.getLocale();
    }

    @Override
    public AccessibleComponent getAccessibleComponent() {
      return this;
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    // AccessibleComponent methods

    @Override
    public Color getBackground() {
      return JBTabsImpl.this.getBackground();
    }

    @Override
    public void setBackground(Color c) {
      JBTabsImpl.this.setBackground(c);
    }

    @Override
    public Color getForeground() {
      return JBTabsImpl.this.getForeground();
    }

    @Override
    public void setForeground(Color c) {
      JBTabsImpl.this.setForeground(c);
    }

    @Override
    public Cursor getCursor() {
      return JBTabsImpl.this.getCursor();
    }

    @Override
    public void setCursor(Cursor c) {
      JBTabsImpl.this.setCursor(c);
    }

    @Override
    public Font getFont() {
      return JBTabsImpl.this.getFont();
    }

    @Override
    public void setFont(Font f) {
      JBTabsImpl.this.setFont(f);
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
      return JBTabsImpl.this.getFontMetrics(f);
    }

    @Override
    public boolean isEnabled() {
      return getTabInfo().isEnabled();
    }

    @Override
    public void setEnabled(boolean b) {
      getTabInfo().setEnabled(b);
    }

    @Override
    public boolean isVisible() {
      return !getTabInfo().isHidden();
    }

    @Override
    public void setVisible(boolean b) {
      getTabInfo().setHidden(!b);
    }

    @Override
    public boolean isShowing() {
      return JBTabsImpl.this.isShowing();
    }

    @Override
    public boolean contains(Point p) {
      Rectangle r = getBounds();
      return r.contains(p);
    }

    @Override
    public Point getLocationOnScreen() {
      Point parentLocation = JBTabsImpl.this.getLocationOnScreen();
      Point componentLocation = getLocation();
      componentLocation.translate(parentLocation.x, parentLocation.y);
      return componentLocation;
    }

    @Override
    public Point getLocation() {
      Rectangle r = getBounds();
      return new Point(r.x, r.y);
    }

    @Override
    public void setLocation(Point p) {
      // do nothing
    }

    /**
     * Returns the bounds of tab.  The bounds are with respect to the JBTabsImpl coordinate space.
     */
    @Override
    public Rectangle getBounds() {
      return getTabLabel().getBounds();
    }

    @Override
    public void setBounds(Rectangle r) {
      // do nothing
    }

    @Override
    public Dimension getSize() {
      Rectangle r = getBounds();
      return new Dimension(r.width, r.height);
    }

    @Override
    public void setSize(Dimension d) {
      // do nothing
    }

    @Override
    public Accessible getAccessibleAt(Point p) {
      if (myComponent instanceof Accessible) {
        return (Accessible) myComponent;
      } else {
        return null;
      }
    }

    @Override
    public boolean isFocusTraversable() {
      return false;
    }

    @Override
    public void requestFocus() {
      // do nothing
    }

    @Override
    public void addFocusListener(FocusListener l) {
      // do nothing
    }

    @Override
    public void removeFocusListener(FocusListener l) {
      // do nothing
    }

    @Override
    public AccessibleIcon [] getAccessibleIcon() {
      AccessibleIcon accessibleIcon = null;

      if (getTabInfo().getIcon() instanceof ImageIcon) {
        AccessibleContext ac =
          ((ImageIcon)getTabInfo().getIcon()).getAccessibleContext();
        accessibleIcon = (AccessibleIcon)ac;
      }

      if (accessibleIcon != null) {
        AccessibleIcon [] returnIcons = new AccessibleIcon[1];
        returnIcons[0] = accessibleIcon;
        return returnIcons;
      } else {
        return null;
      }
    }

    // AccessibleAction methods

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      if (i != 0)
        return null;

      return "Activate";
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i != 0)
        return false;

      JBTabsImpl.this.select(getTabInfo(), true);
      return true;
    }
  }

  /**
   * @deprecated unused. You should move the painting logic to an implementation of {@link JBTabPainter} interface }
   */
  @Deprecated
  public int getActiveTabUnderlineHeight() {
    return 0;
  }

  /**
   * @deprecated unused in current realization.
   */
  @Deprecated
  protected static class ShapeInfo {
    public ShapeInfo() {}
    public ShapeTransform path;
    public ShapeTransform fillPath;
    public ShapeTransform labelPath;
    public int labelBottomY;
    public int labelTopY;
    public int labelLeftX;
    public int labelRightX;
    public Insets insets;
    public Color from;
    public Color to;
  }
}
