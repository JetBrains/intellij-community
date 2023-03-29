// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.rd.GraphicsExKt;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBThinOverlappingScrollBar;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.hover.HoverListener;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowPassInfo;
import com.intellij.ui.tabs.impl.table.TableLayout;
import com.intellij.ui.tabs.impl.table.TablePassInfo;
import com.intellij.ui.tabs.impl.themes.TabTheme;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Producer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.LazyUiDisposable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

@DirtyUI
public class JBTabsImpl extends JComponent
  implements JBTabsEx, PropertyChangeListener, TimerListener, DataProvider, PopupMenuListener, JBTabsPresentation, Queryable,
             UISettingsListener, QuickActionProvider, MorePopupAware, Accessible {

  public static final Key<Boolean> PINNED = Key.create("pinned");

  public static final Key<Integer> SIDE_TABS_SIZE_LIMIT_KEY = Key.create("SIDE_TABS_SIZE_LIMIT_KEY");

  private static final Key<Integer> HIDDEN_INFOS_SELECT_INDEX_KEY = Key.create("HIDDEN_INFOS_SELECT_INDEX");

  public static final int MIN_TAB_WIDTH = JBUIScale.scale(75);
  public static final int DEFAULT_MAX_TAB_WIDTH = JBUIScale.scale(300);

  private static final Comparator<TabInfo> ABC_COMPARATOR = (o1, o2) -> StringUtil.naturalCompare(o1.getText(), o2.getText());
  private static final Logger LOG = Logger.getInstance(JBTabsImpl.class);
  private static final int SCROLL_BAR_THICKNESS = 3;

  private final List<@NotNull TabInfo> myVisibleInfos = new ArrayList<>();
  private final Map<TabInfo, AccessibleTabPage> myInfo2Page = new HashMap<>();
  private final Map<TabInfo, Integer> myHiddenInfos = new HashMap<>();

  private TabInfo mySelectedInfo;
  public final Map<TabInfo, TabLabel> myInfo2Label = new HashMap<>();
  public final Map<TabInfo, Toolbar> myInfo2Toolbar = new HashMap<>();
  public final ActionToolbar myMoreToolbar;
  public @Nullable ActionToolbar myEntryPointToolbar;
  public final NonOpaquePanel myTitleWrapper = new NonOpaquePanel();
  public Dimension myHeaderFitSize;

  private Insets myInnerInsets = JBInsets.emptyInsets();

  private final List<EventListener> myTabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<TabsListener> myTabListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myFocused;

  private Supplier<? extends ActionGroup> myPopupGroup;
  private String myPopupPlace;

  TabInfo myPopupInfo;
  final DefaultActionGroup myNavigationActions;

  final PopupMenuListener myPopupListener;
  JPopupMenu myActivePopup;

  public boolean myHorizontalSide = true;

  private boolean mySideComponentOnTabs = true;

  private boolean mySideComponentBefore = true;

  private final int mySeparatorWidth = JBUI.scale(1);

  private DataProvider myDataProvider;

  private final WeakHashMap<Component, Component> myDeferredToRemove = new WeakHashMap<>();

  private SingleRowLayout mySingleRowLayout;
  private TableLayout myTableLayout = createTableLayout();
  // it's an invisible splitter intended for changing size of tab zone
  private final TabsSideSplitter mySplitter = new TabsSideSplitter(this);


  private TabLayout myLayout;
  private LayoutPassInfo myLastLayoutPass;

  public boolean myForcedRelayout;

  private UiDecorator myUiDecorator;
  static final UiDecorator ourDefaultDecorator = new DefaultDecorator();

  private boolean myPaintFocus;

  private boolean myHideTabs;
  private boolean myHideTopPanel;
  private @Nullable Project myProject;

  private boolean myRequestFocusOnLastFocusedComponent;
  private boolean myListenerAdded;
  final Set<TabInfo> myAttractions = new HashSet<>();
  private final Animator myAnimator;
  private List<TabInfo> myAllTabs;
  private @NotNull IdeFocusManager myFocusManager;
  private static final boolean myAdjustBorders = true;
  private final Set<JBTabsImpl> myNestedTabs = new HashSet<>();

  boolean myAddNavigationGroup = true;

  private Color myActiveTabFillIn;

  private boolean myTabLabelActionsAutoHide;

  private final TabActionsAutoHideListener myTabActionsAutoHideListener = new TabActionsAutoHideListener();
  private Disposable myTabActionsAutoHideListenerDisposable = Disposer.newDisposable();
  private IdeGlassPane myGlassPane;
  private static final @NonNls String LAYOUT_DONE = "Layout.done";

  private TimedDeadzone.Length myTabActionsMouseDeadzone = TimedDeadzone.DEFAULT;

  private long myRemoveDeferredRequest;

  private JBTabsPosition myPosition = JBTabsPosition.top;

  private final JBTabsBorder myBorder = createTabBorder();
  private final BaseNavigationAction myNextAction;
  private final BaseNavigationAction myPrevAction;

  private boolean myTabDraggingEnabled;
  private DragHelper myDragHelper;
  private boolean myNavigationActionsEnabled = true;

  protected TabInfo myDropInfo;
  private int myDropInfoIndex;

  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT,
    -1})
  private int myDropSide = -1;
  protected boolean myShowDropLocation = true;

  private TabInfo myOldSelection;
  private SelectionChangeHandler mySelectionChangeHandler;

  private Runnable myDeferredFocusRequest;
  private int myFirstTabOffset;

  private final TabPainterAdapter myTabPainterAdapter = createTabPainterAdapter();
  protected final JBTabPainter myTabPainter = myTabPainterAdapter.getTabPainter();
  private boolean myAlphabeticalMode;
  private boolean mySupportsCompression;
  private String myEmptyText;
  private boolean myMouseInsideTabsArea;
  private boolean myRemoveNotifyInProgress;

  private boolean mySingleRow = true;

  protected JBTabsBorder createTabBorder() {
    return new JBDefaultTabsBorder(this);
  }

  public JBTabPainter getTabPainter() {
    return myTabPainter;
  }

  TabPainterAdapter getTabPainterAdapter() {
    return myTabPainterAdapter;
  }

  protected TabPainterAdapter createTabPainterAdapter() {
    return new DefaultTabPainterAdapter(JBTabPainter.getDEFAULT());
  }

  private TabLabel tabLabelAtMouse;

  private final JBScrollBar myScrollBar;
  private final ChangeListener myScrollBarChangeListener;
  private boolean myScrollBarOn = false;

  public JBTabsImpl(@NotNull Project project) {
    this(project, project);
  }

  private JBTabsImpl(@NotNull Project project, @NotNull Disposable parent) {
    this(project, IdeFocusManager.getInstance(project), parent);
  }

  public JBTabsImpl(@Nullable Project project, @Nullable IdeFocusManager focusManager, @NotNull Disposable parentDisposable) {
    myProject = project;
    myFocusManager = focusManager == null ? getGlobalInstance() : focusManager;

    setOpaque(true);
    setBackground(myTabPainter.getBackgroundColor());

    setBorder(myBorder);

    myNavigationActions = new DefaultActionGroup();

    myNextAction = new SelectNextAction(this, parentDisposable);
    myPrevAction = new SelectPreviousAction(this, parentDisposable);

    myNavigationActions.add(myNextAction);
    myNavigationActions.add(myPrevAction);

    setUiDecorator(null);

    mySingleRowLayout = createSingleRowLayout();
    setLayout(mySingleRowLayout);

    mySplitter.getDivider().setOpaque(false);

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

    ActionManager actionManager = ActionManager.getInstance();
    myMoreToolbar = createToolbar(new DefaultActionGroup(actionManager.getAction("TabList")));
    add(myMoreToolbar.getComponent());

    DefaultActionGroup entryPointActionGroup = getEntryPointActionGroup();
    if (entryPointActionGroup != null) {
      myEntryPointToolbar = createToolbar(entryPointActionGroup);
      add(myEntryPointToolbar.getComponent());
    }
    else {
      myEntryPointToolbar = null;
    }

    add(myTitleWrapper);
    Disposer.register(parentDisposable, () -> {
      setTitleProducer(null);
    });

    // This scroll pane won't be shown on screen, it is needed only to handle scrolling events and properly update scrolling model
    JScrollPane fakeScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    myScrollBar = new JBThinOverlappingScrollBar(isHorizontalTabs() ? Adjustable.HORIZONTAL : Adjustable.VERTICAL);
    fakeScrollPane.setVerticalScrollBar(myScrollBar);
    fakeScrollPane.setHorizontalScrollBar(myScrollBar);
    fakeScrollPane.setVisible(true);
    fakeScrollPane.setBounds(0, 0, 0, 0);

    add(myScrollBar);
    addMouseWheelListener(event -> {
      int modifiers = UIUtil.getAllModifiers(event) | (isHorizontalTabs() ? InputEvent.SHIFT_DOWN_MASK : 0);
      MouseWheelEvent e = MouseEventAdapter.convert(event, fakeScrollPane, event.getID(), event.getWhen(),
                                                    modifiers, event.getX(), event.getY());
      MouseEventAdapter.redispatch(e, fakeScrollPane);
    });
    AWTEventListener listener = new AWTEventListener() {
      final Alarm afterScroll = new Alarm(parentDisposable);

      @Override
      public void eventDispatched(AWTEvent event) {
        Rectangle tabRectangle = null;
        if (mySingleRowLayout.myLastSingRowLayout != null) {
          tabRectangle = mySingleRowLayout.myLastSingRowLayout.tabRectangle;
        }
        else if (myTableLayout.myLastTableLayout != null) {
          tabRectangle = myTableLayout.myLastTableLayout.tabRectangle;
        }
        if (tabRectangle == null) return;

        MouseEvent me = (MouseEvent)event;
        Point point = me.getPoint();
        SwingUtilities.convertPointToScreen(point, me.getComponent());
        Rectangle rect = getVisibleRect();
        rect = rect.intersection(tabRectangle);
        Point p = rect.getLocation();
        SwingUtilities.convertPointToScreen(p, JBTabsImpl.this);
        rect.setLocation(p);
        boolean inside = rect.contains(point);
        if (inside != myMouseInsideTabsArea) {
          myMouseInsideTabsArea = inside;
          afterScroll.cancelAllRequests();
          if (!inside) {
            afterScroll.addRequest(() -> {
              // here is no any "isEDT"-checks <== this task should be called in EDT <==
              // <== Alarm instance executes tasks in EDT <== used constructor of Alarm uses EDT for tasks by default
              if (!myMouseInsideTabsArea) {
                relayout(false, false);
              }
            }, 500);
          }
        }
      }
    };
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
    Disposer.register(parentDisposable, () -> {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      if (toolkit != null) {
        toolkit.removeAWTEventListener(listener);
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

    LazyUiDisposable<JBTabsImpl> listener1 = new LazyUiDisposable<>(parentDisposable, this, this) {
      @Override
      protected void initialize(@NotNull Disposable parent, @NotNull JBTabsImpl child, @Nullable Project project) {
        if (myProject == null && project != null) {
          myProject = project;
        }

        Disposer.register(parentDisposable, myAnimator);
        Disposer.register(parentDisposable, () -> removeTimerUpdate());

        IdeGlassPane gp = IdeGlassPaneUtil.find(child);
        myTabActionsAutoHideListenerDisposable = Disposer.newDisposable("myTabActionsAutoHideListener");
        Disposer.register(parentDisposable, myTabActionsAutoHideListenerDisposable);
        gp.addMouseMotionPreprocessor(myTabActionsAutoHideListener, myTabActionsAutoHideListenerDisposable);
        myGlassPane = gp;

        StartupUiUtil.addAwtListener(__ -> {
          if (!JBPopupFactory.getInstance().getChildPopups(JBTabsImpl.this).isEmpty()) return;
          processFocusChange();
        }, AWTEvent.FOCUS_EVENT_MASK, parentDisposable);

        myDragHelper = createDragHelper(child, parentDisposable);
        myDragHelper.start();

        if (myProject != null && myFocusManager == getGlobalInstance()) {
          myFocusManager = IdeFocusManager.getInstance(myProject);
        }
      }
    };
    listener1.setupListeners();
    ClientProperty.put(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<? extends Component>)(Iterable<JComponent>)() -> {
      return JBIterable.from(getVisibleInfos())
        .filter(Conditions.not(Conditions.is(mySelectedInfo)))
        .transform(info -> info.getComponent()).iterator();
    });

    HoverListener hoverListener = new HoverListener() {
      @Override
      public void mouseEntered(@NotNull Component component, int x, int y) {
        toggleScrollBar(isInsideTabsArea(x, y));
      }

      @Override
      public void mouseMoved(@NotNull Component component, int x, int y) {
        toggleScrollBar(isInsideTabsArea(x, y));
      }

      @Override
      public void mouseExited(@NotNull Component component) {
        toggleScrollBar(false);
      }
    };
    hoverListener.addTo(this);

    myScrollBarChangeListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateTabsOffsetFromScrollBar();
      }
    };
  }

  private boolean isInsideTabsArea(int x, int y) {
    Dimension area = myHeaderFitSize;
    if (myTableLayout.myLastTableLayout != null) {
      area = myTableLayout.myLastTableLayout.tabRectangle.getSize();
    }

    if (area == null) return false;

    return switch (getTabsPosition()) {
      case top -> y <= area.height;
      case left -> x <= area.width;
      case bottom -> y >= getHeight() - area.height;
      case right -> x >= getWidth() - area.width;
    };
  }

  private void toggleScrollBar(boolean isOn) {
    if (isOn == myScrollBarOn) return;
    myScrollBarOn = isOn;
    myScrollBar.toggle(isOn);
  }

  private @NotNull ActionToolbar createToolbar(ActionGroup group) {
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, group, true);
    toolbar.setTargetComponent(this);
    toolbar.getComponent().setBorder(JBUI.Borders.empty());
    toolbar.getComponent().setOpaque(false);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    return toolbar;
  }

  protected @Nullable DefaultActionGroup getEntryPointActionGroup() {
    return null;
  }

  private Rectangle getScrollBarBounds() {
    if (!isWithScrollBar() || isHideTabs()) return new Rectangle(0, 0, 0, 0);

    return switch (getTabsPosition()) {
      case left -> {
        if (ExperimentalUI.isNewUI()) {
          Rectangle tabsRect = myLastLayoutPass.getHeaderRectangle();
          if (tabsRect != null) {
            yield new Rectangle(tabsRect.x + tabsRect.width - SCROLL_BAR_THICKNESS, 0, SCROLL_BAR_THICKNESS, getHeight());
          }
          else {
            yield new Rectangle(0, 0, 0, 0);
          }
        }
        else {
          yield new Rectangle(0, 0, SCROLL_BAR_THICKNESS, getHeight());
        }
      }
      case right -> new Rectangle(getWidth() - SCROLL_BAR_THICKNESS, 0, SCROLL_BAR_THICKNESS, getHeight());
      case top -> new Rectangle(0, 1, getWidth(), SCROLL_BAR_THICKNESS);
      case bottom -> new Rectangle(0, getHeight() - SCROLL_BAR_THICKNESS, getWidth(), SCROLL_BAR_THICKNESS);
    };
  }

  private BoundedRangeModel getScrollBarModel() {
    return myScrollBar.getModel();
  }

  public boolean isWithScrollBar() {
    return myLayout.isWithScrollBar();
  }

  protected @NotNull DragHelper createDragHelper(@NotNull JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
    return new DragHelper(tabs, parentDisposable);
  }

  public boolean isMouseInsideTabsArea() {
    return myMouseInsideTabsArea;
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    for (Map.Entry<TabInfo, TabLabel> entry : myInfo2Label.entrySet()) {
      TabInfo info = entry.getKey();
      TabLabel label = entry.getValue();

      info.revalidate();
      label.setTabActions(info.getTabLabelActions());
    }
    updateRowLayout();
  }

  private void updateRowLayout() {
    if (getTabsPosition() != JBTabsPosition.top) {
      mySingleRow = true;
    }
    boolean forceTableLayout = getTabsPosition() == JBTabsPosition.top
                               && supportsTableLayoutAsSingleRow()
                               && TabLayout.showPinnedTabsSeparately();
    boolean useTableLayout = !isSingleRow() || forceTableLayout;
    if (useTableLayout) {
      myTableLayout = createTableLayout();
    }
    else {
      mySingleRowLayout = createSingleRowLayout();
    }
    TabLayout layout = useTableLayout ? myTableLayout : mySingleRowLayout;
    layout.scroll(getScrollBarModel().getValue()); // set current scroll value to new layout
    setLayout(layout);
    relayout(true, true);
  }

  protected boolean supportsTableLayoutAsSingleRow() {
    return false;
  }

  protected TableLayout createTableLayout() {
    boolean isWithScrollBar = ExperimentalUI.isEditorTabsWithScrollBar()
                              && isSingleRow()
                              && getTabsPosition() == JBTabsPosition.top
                              && supportsTableLayoutAsSingleRow()
                              && TabLayout.showPinnedTabsSeparately()
                              && (!supportsCompression() || UISettings.getInstance().getHideTabsIfNeeded());
    return new TableLayout(this, isWithScrollBar);
  }

  protected SingleRowLayout createSingleRowLayout() {
    return new ScrollableSingleRowLayout(this);
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

    if (old != null) {
      old.revalidate();
      old.repaint();
    }

    if (tabLabelAtMouse != null) {
      tabLabelAtMouse.revalidate();
      tabLabelAtMouse.repaint();
    }
  }

  void unHover(TabLabel label) {
    if (tabLabelAtMouse == label) {
      tabLabelAtMouse = null;
      label.revalidate();
      label.repaint();
    }
  }

  public final boolean isHoveredTab(TabLabel label) {
    return label != null && label == tabLabelAtMouse;
  }

  public boolean isActiveTabs(TabInfo info) {
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

  public void addNestedTabs(@NotNull JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
    myNestedTabs.add(tabs);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myNestedTabs.remove(tabs);
      }
    });
  }

  public boolean isDragOut(TabLabel label, int deltaX, int deltaY) {
    return getEffectiveLayout().isDragOut(label, deltaX, deltaY);
  }

  boolean ignoreTabLabelLimitedWidthWhenPaint() {
    return myLayout instanceof ScrollableSingleRowLayout
           || (myLayout instanceof TableLayout && TabLayout.showPinnedTabsSeparately());
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

  void resetTabsCache() {
    EDT.assertIsEdt();
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

    getScrollBarModel().addChangeListener(myScrollBarChangeListener);

    if (myDeferredFocusRequest != null) {
      final Runnable request = myDeferredFocusRequest;
      myDeferredFocusRequest = null;

      request.run();
    }
  }

  @Override
  public void remove(int index) {
    if (myRemoveNotifyInProgress) {
      LOG.warn(new IllegalStateException("removeNotify in progress"));
    }
    super.remove(index);
  }

  @Override
  public void removeAll() {
    if (myRemoveNotifyInProgress) {
      LOG.warn(new IllegalStateException("removeNotify in progress"));
    }
    super.removeAll();
  }

  @Override
  public void removeNotify() {
    try {
      myRemoveNotifyInProgress = true;
      super.removeNotify();
    }
    finally {
      myRemoveNotifyInProgress = false;
    }

    setFocused(false);

    removeTimerUpdate();

    getScrollBarModel().removeChangeListener(myScrollBarChangeListener);

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
      ActionManager.getInstance().addTimerListener(this);
      myListenerAdded = true;
    }
  }

  private void removeTimerUpdate() {
    if (myListenerAdded) {
      ActionManager.getInstance().removeTimerListener(this);
      myListenerAdded = false;
    }
  }

  public void layoutComp(SingleRowPassInfo data, int deltaX, int deltaY, int deltaWidth, int deltaHeight) {
    JComponent hToolbar = data.hToolbar.get();
    JComponent vToolbar = data.vToolbar.get();
    if (hToolbar != null) {
      final int toolbarHeight = hToolbar.getPreferredSize().height;
      final Rectangle compRect = layoutComp(deltaX, toolbarHeight + deltaY, data.comp.get(), deltaWidth, deltaHeight);
      layout(hToolbar, compRect.x, compRect.y - toolbarHeight, compRect.width, toolbarHeight);
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

  private void setDropInfoIndex(int dropInfoIndex) {
    myDropInfoIndex = dropInfoIndex;
  }

  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT,
    -1})
  private void setDropSide(int side) {
    myDropSide = side;
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

  /**
   * TODO use RdGraphicsExKt#childAtMouse(IdeGlassPane, Container)
   */
  @Deprecated
  final class TabActionsAutoHideListener extends MouseMotionAdapter implements Weighted {
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
      if (!myTabLabelActionsAutoHide || myLastOverPoint == null) {
        return;
      }

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
    if (isHideTabs()) return;

    final Ref<Boolean> changed = new Ref<>(Boolean.FALSE);
    for (final TabInfo eachInfo : myInfo2Label.keySet()) {
      final boolean changes = myInfo2Label.get(eachInfo).updateTabActions();
      changed.set(changed.get().booleanValue() || changes);
    }

    if (changed.get()) {
      revalidateAndRepaint();
    }
  }

  public @NotNull Dimension getEntryPointPreferredSize() {
    if (myEntryPointToolbar == null) return new Dimension();
    return myEntryPointToolbar.getComponent().getPreferredSize();
  }

  // Returns default one action horizontal toolbar size (26x24)
  public @NotNull Dimension getMoreToolbarPreferredSize() {
    Dimension baseSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;
    return new Dimension(baseSize.width + JBUI.scale(4), baseSize.height + JBUI.scale(2));
  }

  private Rectangle getEntryPointRect() {
    if (myLayout instanceof SingleRowLayout) {
      SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
      return lastLayout != null ? lastLayout.entryPointRect : null;
    }
    if (myLayout instanceof TableLayout) {
      TablePassInfo lastLayout = myTableLayout.myLastTableLayout;
      return lastLayout != null ? lastLayout.entryPointRect : null;
    }
    return null;
  }

  private Rectangle getMoreRect() {
    if (myLayout instanceof SingleRowLayout) {
      SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
      return lastLayout != null ? lastLayout.moreRect : null;
    }
    if (myLayout instanceof TableLayout) {
      TablePassInfo lastLayout = myTableLayout.myLastTableLayout;
      return lastLayout != null ? lastLayout.moreRect : null;
    }
    return null;
  }

  private Rectangle getTitleRect() {
    if (myLayout instanceof SingleRowLayout) {
      SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
      return lastLayout != null ? lastLayout.titleRect : null;
    }
    if (myLayout instanceof TableLayout) {
      TablePassInfo lastLayout = myTableLayout.myLastTableLayout;
      return lastLayout != null ? lastLayout.titleRect : null;
    }
    return null;
  }

  @Override
  public void setTitleProducer(@Nullable Producer<? extends Pair<Icon, String>> titleProducer) {
    myTitleWrapper.removeAll();
    if (titleProducer != null) {
      ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, new DefaultActionGroup(new TitleAction(titleProducer)), true);
      toolbar.setTargetComponent(null);
      toolbar.setMiniMode(true);
      myTitleWrapper.setContent(toolbar.getComponent());
    }
  }

  @Override
  public boolean canShowMorePopup() {
    Rectangle rect = getMoreRect();
    return rect != null && !rect.isEmpty();
  }

  @Override
  public @Nullable JBPopup showMorePopup() {
    Rectangle rect = getMoreRect();
    if (rect == null) return null;

    List<TabInfo> hiddenInfos = ContainerUtil.filter(getVisibleInfos(), tabInfo -> mySingleRowLayout.isTabHidden(tabInfo));
    if (ExperimentalUI.isNewUI()) {
      return showListPopup(rect, hiddenInfos);
    }
    else {
      return showTabLabelsPopup(rect, hiddenInfos);
    }
  }

  private JBPopup showListPopup(Rectangle rect, List<TabInfo> hiddenInfos) {
    int separatorIndex = ContainerUtil.indexOf(hiddenInfos, info -> {
      TabLabel label = myInfo2Label.get(info);
      return getPosition().isSide() ? label.getY() >= 0 : label.getX() >= 0;
    });
    TabInfo separatorInfo = separatorIndex > 0 ? hiddenInfos.get(separatorIndex) : null;

    HiddenInfosListPopupStep step = new HiddenInfosListPopupStep(hiddenInfos, separatorInfo);
    Integer selectedIndex = ClientProperty.get(this, HIDDEN_INFOS_SELECT_INDEX_KEY);
    if (selectedIndex != null) {
      step.setDefaultOptionIndex(selectedIndex);
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(myProject, step, renderer -> {
      ListItemDescriptor<TabInfo> descriptor = new ListItemDescriptorAdapter<>() {
        @Override
        public @NotNull String getTextFor(TabInfo value) {
          return value.getText();
        }

        @Override
        public @Nullable Icon getIconFor(TabInfo value) {
          return value.getIcon();
        }

        @Override
        public boolean hasSeparatorAboveOf(TabInfo value) {
          return value == separatorInfo;
        }
      };
      return new GroupedItemsListRenderer<>(descriptor) {
        private static final Key<Integer> HOVER_INDEX_KEY = Key.create("HOVER_INDEX");
        private static final Key<TabInfo> TAB_INFO_KEY = Key.create("TAB_INFO");
        private static final Key<Boolean> SELECTED_KEY = Key.create("SELECTED");
        JPanel component;
        JLabel iconLabel;
        SimpleColoredComponent textLabel;
        JLabel actionLabel;
        MouseAdapter listMouseListener;

        @Override
        protected JComponent createItemComponent() {
          // there is the separate label 'textLabel', but the original one still should be created,
          // as it is used from the GroupedElementsRenderer.configureComponent
          createLabel();

          component = new JPanel();
          BoxLayout layout = new BoxLayout(component, BoxLayout.X_AXIS);
          component.setLayout(layout);
          // painting underline for the selected tab
          component.setBorder(new Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
              if (ClientProperty.get(c, SELECTED_KEY) != Boolean.TRUE) {
                return;
              }
              int inset = JBUI.scale(2);
              int arc = JBUI.scale(4);
              TabTheme theme = getTabPainter().getTabTheme();
              Rectangle rect = new Rectangle(x, y + inset, theme.getUnderlineHeight(), height - inset * 2);
              GraphicsExKt.fill2DRoundRect((Graphics2D)g, rect, arc, theme.getUnderlineColor());
            }

            @Override
            public Insets getBorderInsets(Component c) {
              return JBInsets.create(new Insets(0, 9, 0, 3));
            }

            @Override
            public boolean isBorderOpaque() {
              return true;
            }
          });

          UISettings settings = UISettings.getInstance();
          if (!settings.getCloseTabButtonOnTheRight()) {
            addActionLabel();
            int gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2;
            component.add(Box.createRigidArea(new Dimension(gap, 0)));
          }

          iconLabel = new JLabel();
          component.add(iconLabel);
          int gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2;
          component.add(Box.createRigidArea(new Dimension(gap, 0)));

          textLabel = new SimpleColoredComponent() {
            @Override
            public Dimension getMaximumSize() {
              return getPreferredSize();
            }
          };
          textLabel.setMyBorder(null);
          textLabel.setIpad(JBInsets.emptyInsets());
          textLabel.setOpaque(true);
          component.add(textLabel);

          if (settings.getCloseTabButtonOnTheRight()) {
            component.add(Box.createRigidArea(new JBDimension(30, 0)));
            component.add(Box.createHorizontalGlue());
            addActionLabel();
          }

          JComponent result = layoutComponent(component);
          if (result instanceof SelectablePanel selectablePanel) {
            selectablePanel.setBorder(JBUI.Borders.empty(0, 5));
            selectablePanel.setSelectionInsets(JBInsets.create(0, 5));
            selectablePanel.setPreferredHeight(JBUI.scale(26));
          }
          return result;
        }

        private void addActionLabel() {
          actionLabel = new JLabel();
          component.add(actionLabel);
        }

        @Override
        protected void customizeComponent(JList<? extends TabInfo> list, TabInfo info, boolean isSelected) {
          if (actionLabel != null) {
            boolean isHovered = Objects.equals(ClientProperty.get(list, HOVER_INDEX_KEY), myCurrentIndex);
            Icon icon = JBTabsImpl.this.getTabActionIcon(info, isHovered);
            actionLabel.setIcon(icon);
            ClientProperty.put(actionLabel, TAB_INFO_KEY, info);

            addMouseListener(list);
          }

          TabInfo selectedInfo = getSelectedInfo();
          Icon icon = info.getIcon();
          if (icon != null && info != selectedInfo) {
            icon = IconLoader.getTransparentIcon(icon, JBUI.CurrentTheme.EditorTabs.unselectedAlpha());
          }
          iconLabel.setIcon(icon);

          textLabel.clear();
          info.getColoredText().appendToComponent(textLabel);

          Color customBackground = info.getTabColor();
          myRendererComponent.setBackground(customBackground != null ? customBackground : JBUI.CurrentTheme.Popup.BACKGROUND);

          ClientProperty.put(component, SELECTED_KEY, info == selectedInfo ? true : null);

          component.invalidate();
        }

        @Override
        protected void setComponentIcon(Icon icon, Icon disabledIcon) {
          // icon will be set in customizeComponent
        }

        @Override
        protected SeparatorWithText createSeparator() {
          Insets labelInsets = JBUI.CurrentTheme.Popup.separatorLabelInsets();
          return new GroupHeaderSeparator(labelInsets);
        }

        private void addMouseListener(JList<? extends TabInfo> list) {
          if (listMouseListener != null) return;
          listMouseListener = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
              Point point = e.getLocationOnScreen();
              SwingUtilities.convertPointFromScreen(point, list);
              int hoveredIndex = list.locationToIndex(point);
              Component renderer = ListUtil.getDeepestRendererChildComponentAt(list, e.getPoint());
              updateHoveredIconIndex(ClientProperty.get(renderer, TAB_INFO_KEY) != null ? hoveredIndex : -1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
              updateHoveredIconIndex(-1);
            }

            private void updateHoveredIconIndex(int hoveredIndex) {
              Integer oldIndex = ClientProperty.get(list, HOVER_INDEX_KEY);
              ClientProperty.put(list, HOVER_INDEX_KEY, hoveredIndex);
              if (!Objects.equals(oldIndex, hoveredIndex)) {
                list.repaint();
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              Point point = e.getLocationOnScreen();
              SwingUtilities.convertPointFromScreen(point, list);
              int clickedIndex = list.locationToIndex(point);
              Component renderer = ListUtil.getDeepestRendererChildComponentAt(list, e.getPoint());
              if (!(renderer instanceof JLabel label)) {
                return;
              }

              TabInfo tabInfo = ClientProperty.get(label, TAB_INFO_KEY);
              if (tabInfo == null) {
                return;
              }

              // The last one is expected to be 'CloseTab'
              AnAction tabAction = tabInfo.getTabLabelActions() != null
                                   ? ArrayUtil.getLastElement(tabInfo.getTabLabelActions().getChildren(null))
                                   : null;
              if (tabAction == null && !tabInfo.isPinned()) {
                return;
              }

              boolean clickToUnpin = false;
              if (tabInfo.isPinned()) {
                if (tabAction != null) {
                  JComponent component = tabInfo.getComponent();
                  boolean wasShowing = UIUtil.isShowing(component);
                  try {
                    UIUtil.markAsShowing(component, true);
                    ActionManager.getInstance().tryToExecute(tabAction, e, tabInfo.getComponent(), tabInfo.getTabActionPlace(), true);
                  }
                  finally {
                    UIUtil.markAsShowing(component, wasShowing);
                  }
                  clickToUnpin = true;
                }
              }
              if (!clickToUnpin) {
                removeTab(tabInfo);
              }

              e.consume();
              int indexToSelect = Math.min(clickedIndex, list.getModel().getSize());
              ClientProperty.put(JBTabsImpl.this, HIDDEN_INFOS_SELECT_INDEX_KEY, indexToSelect);
              step.selectTab = false;  // do not select current tab, because we already handled other action: close or unpin

              JBPopup curPopup = PopupUtil.getPopupContainerFor(list);
              if (curPopup != null) {
                Component button = PopupUtil.getPopupToggleComponent(curPopup);
                curPopup.cancel();
                if (list.getModel().getSize() > 0) {
                  JBPopup newPopup = showMorePopup();
                  if (newPopup != null) {
                    PopupUtil.setPopupToggleComponent(newPopup, button);
                  }
                }
              }
            }
          };
          MouseListener[] listeners = list.getMouseListeners();
          MouseMotionListener[] motionListeners = list.getMouseMotionListeners();
          Arrays.stream(listeners).forEach(list::removeMouseListener);
          Arrays.stream(motionListeners).forEach(list::removeMouseMotionListener);
          list.addMouseListener(listMouseListener);
          list.addMouseMotionListener(listMouseListener);
          Arrays.stream(listeners).forEach(list::addMouseListener);
          Arrays.stream(motionListeners).forEach(list::addMouseMotionListener);
        }
      };
    });

    popup.getContent().putClientProperty(MorePopupAware.class, Boolean.TRUE);
    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          ClientProperty.put(JBTabsImpl.this, HIDDEN_INFOS_SELECT_INDEX_KEY, null);
        });
      }
    });
    popup.show(new RelativePoint(this, new Point(rect.x, rect.y + rect.height)));
    return popup;
  }

  // returns the icon that will be used in the hidden tabs list
  protected @Nullable Icon getTabActionIcon(@NotNull TabInfo info, boolean isHovered) {
    boolean hasActions = info.getTabLabelActions() != null && info.getTabLabelActions().getChildren(null).length > 0;
    Icon icon;
    if (hasActions) {
      icon = isHovered ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close;
    }
    else {
      icon = EmptyIcon.ICON_16;
    }
    if (info.isPinned()) {
      icon = AllIcons.Actions.PinTab;
    }
    return icon;
  }

  private class HiddenInfosListPopupStep extends BaseListPopupStep<TabInfo> {
    private final TabInfo separatorInfo;
    boolean selectTab = true;

    private HiddenInfosListPopupStep(@NotNull List<TabInfo> values, @Nullable TabInfo separatorInfo) {
      super(null, values);
      this.separatorInfo = separatorInfo;
    }

    @Override
    public @Nullable PopupStep<?> onChosen(TabInfo selectedValue, boolean finalChoice) {
      if (selectTab) {
        select(selectedValue, true);
      }
      else {
        selectTab = true;
      }
      return FINAL_CHOICE;
    }

    @Override
    public @Nullable ListSeparator getSeparatorAbove(TabInfo value) {
      return value == separatorInfo ? new ListSeparator() : null;
    }

    @Override
    public Icon getIconFor(TabInfo value) {
      return value.getIcon();
    }

    @Override
    public @NotNull String getTextFor(TabInfo value) {
      return value.getText();
    }
  }

  private JBPopup showTabLabelsPopup(Rectangle rect, List<TabInfo> hiddenInfos) {
    JPanel gridPanel = new JPanel(new GridLayout(hiddenInfos.size(), 1));
    JScrollPane scrollPane = new JBScrollPane(gridPanel) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (ScreenUtil.getScreenRectangle(JBTabsImpl.this).height < gridPanel.getPreferredSize().height) {
          size.width += UIUtil.getScrollBarWidth();
        }
        return size;
      }
    };
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, null).createPopup();
    for (TabInfo info : hiddenInfos) {
      TabLabel label = createTabLabel(info);
      label.setDoubleBuffered(true);
      label.setText(info.getColoredText());
      label.setIcon(info.getIcon());
      label.setTabActions(info.getTabLabelActions());
      label.setAlignmentToCenter(false);
      label.apply(myUiDecorator != null ? myUiDecorator.getDecoration() : ourDefaultDecorator.getDecoration());
      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.isShiftDown() && !e.isPopupTrigger()) {
            removeTab(info);
            if (canShowMorePopup()) {
              showMorePopup();
            }
            popup.cancel();
          }
          else {
            select(info, true);
          }
        }
      });
      add(label);
      try {
        label.updateTabActions();
      }
      finally {
        remove(label);
      }
      gridPanel.add(label);
    }
    popup.getContent().putClientProperty(MorePopupAware.class, Boolean.TRUE);
    popup.show(new RelativePoint(this, new Point(rect.x, rect.y + rect.height)));
    return popup;
  }

  private @Nullable JComponent getToFocus() {
    final TabInfo info = getSelectedInfo();
    if (LOG.isDebugEnabled()) {
      LOG.debug("selected info: " + info);
    }

    if (info == null) return null;

    JComponent toFocus = null;

    if (isRequestFocusOnLastFocusedComponent() && info.getLastFocusOwner() != null && !isMyChildIsFocusedNow()) {
      toFocus = info.getLastFocusOwner();
      if (LOG.isDebugEnabled()) {
        LOG.debug("last focus owner: " + toFocus);
      }
    }

    if (toFocus == null) {
      toFocus = info.getPreferredFocusableComponent();
      if (LOG.isDebugEnabled()) {
        LOG.debug("preferred focusable component: " + toFocus);
      }

      if (toFocus == null || !toFocus.isShowing()) {
        return null;
      }
      final JComponent policyToFocus = myFocusManager.getFocusTargetFor(toFocus);
      if (LOG.isDebugEnabled()) {
        LOG.debug("focus target: " + policyToFocus);
      }

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
    return toFocus != null ? toFocus.requestFocusInWindow() : super.requestFocusInWindow();
  }


  @Override
  public @NotNull TabInfo addTab(TabInfo info, int index) {
    return addTab(info, index, false, true);
  }

  @Override
  public TabInfo addTabSilently(TabInfo info, int index) {
    return addTab(info, index, false, false);
  }

  private TabInfo addTab(@NotNull TabInfo info, int index, boolean isDropTarget, boolean fireEvents) {
    if (!isDropTarget && getTabs().contains(info)) {
      return getTabs().get(getTabs().indexOf(info));
    }

    info.getChangeSupport().addPropertyChangeListener(this);
    TabLabel label = createTabLabel(info);
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

    updateAll(false);

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

  protected TabLabel createTabLabel(TabInfo info) {
    return new TabLabel(this, info);
  }

  @Override
  public @NotNull TabInfo addTab(TabInfo info) {
    return addTab(info, -1);
  }

  @Override
  public TabLabel getTabLabel(TabInfo info) {
    return myInfo2Label.get(info);
  }

  public @Nullable ActionGroup getPopupGroup() {
    return myPopupGroup != null ? myPopupGroup.get() : null;
  }

  public String getPopupPlace() {
    return myPopupPlace;
  }

  @Override
  public @NotNull JBTabs setPopupGroup(final @NotNull ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup) {
    return setPopupGroup(() -> popupGroup, place, addNavigationGroup);
  }

  @Override
  public @NotNull JBTabs setPopupGroup(@NotNull Supplier<? extends ActionGroup> popupGroup,
                                       @NotNull String place,
                                       boolean addNavigationGroup) {
    myPopupGroup = popupGroup;
    myPopupPlace = place;
    myAddNavigationGroup = addNavigationGroup;
    return this;
  }

  private void updateAll(boolean forcedRelayout) {
    TabInfo toSelect = getSelectedInfo();
    setSelectedInfo(toSelect);
    updateContainer(forcedRelayout, false);
    removeDeferred();
    updateListeners();
    updateTabActions(false);
    updateEnabling();
  }

  private boolean isMyChildIsFocusedNow() {
    Component owner = getFocusOwner();
    if (owner == null) {
      return false;
    }

    if (mySelectedInfo != null && !SwingUtilities.isDescendingFrom(owner, mySelectedInfo.getComponent())) {
      return false;
    }

    return SwingUtilities.isDescendingFrom(owner, this);
  }

  private static @Nullable JComponent getFocusOwner() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return (JComponent)(owner instanceof JComponent ? owner : null);
  }

  @Override
  public @NotNull ActionCallback select(@NotNull TabInfo info, boolean requestFocus) {
    return _setSelected(info, requestFocus, false);
  }

  private @NotNull ActionCallback _setSelected(final TabInfo info, final boolean requestFocus, boolean requestFocusInWindow) {
    if (!isEnabled()) {
      return ActionCallback.REJECTED;
    }

    myMouseInsideTabsArea = false;//temporary state to make selection fully visible (scrolled in view)
    if (mySelectionChangeHandler != null) {
      return mySelectionChangeHandler.execute(info, requestFocus, new ActiveRunnable() {
        @Override
        public @NotNull ActionCallback run() {
          return executeSelectionChange(info, requestFocus, requestFocusInWindow);
        }
      });
    }
    return executeSelectionChange(info, requestFocus, requestFocusInWindow);
  }

  private @NotNull ActionCallback executeSelectionChange(@NotNull TabInfo info, boolean requestFocus, boolean requestFocusInWindow) {
    if (mySelectedInfo != null && mySelectedInfo.equals(info)) {
      if (!requestFocus) {
        return ActionCallback.DONE;
      }

      Component owner = myFocusManager.getFocusOwner();
      JComponent c = info.getComponent();
      if (c != null && owner != null && (c == owner || SwingUtilities.isDescendingFrom(owner, c))) {
        // This might look like a no-op, but in some cases it's not. In particular, it's required when a focus transfer has just been
        // requested to another component. E.g. this happens on 'unsplit' operation when we remove an editor component from UI hierarchy and
        // re-add it at once in a different layout, and want that editor component to preserve focus afterwards.
        return requestFocus(owner, requestFocusInWindow);
      }
      return requestFocus(getToFocus(), requestFocusInWindow);
    }

    if (myRequestFocusOnLastFocusedComponent && mySelectedInfo != null && isMyChildIsFocusedNow()) {
      mySelectedInfo.setLastFocusOwner(getFocusOwnerToStore());
    }

    TabInfo oldInfo = mySelectedInfo;
    setSelectedInfo(info);
    TabInfo newInfo = getSelectedInfo();

    TabLabel label = myInfo2Label.get(info);
    if (label != null) {
      setComponentZOrder(label, 0);
    }
    setComponentZOrder(myScrollBar, 0);

    fireBeforeSelectionChanged(oldInfo, newInfo);
    boolean oldValue = myMouseInsideTabsArea;
    try {
      updateContainer(false, true);
    }
    finally {
      myMouseInsideTabsArea = oldValue;
    }

    fireSelectionChanged(oldInfo, newInfo);

    if (!requestFocus) {
      return removeDeferred();
    }

    JComponent toFocus = getToFocus();
    if (myProject != null && toFocus != null) {
      ActionCallback result = new ActionCallback();
      requestFocus(toFocus, requestFocusInWindow).doWhenProcessed(() -> {
        if (myProject.isDisposed()) {
          result.setRejected();
        }
        else {
          removeDeferred().notifyWhenDone(result);
        }
      });
      return result;
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (requestFocusInWindow) {
          requestFocusInWindow();
        }
        else {
          myFocusManager.requestFocusInProject(this, myProject);
        }
      }, ModalityState.NON_MODAL);
      return removeDeferred();
    }
  }

  protected @Nullable JComponent getFocusOwnerToStore() {
    JComponent owner = getFocusOwner();
    if (owner == null) return null;
    JBTabsImpl tabs = ComponentUtil.getParentOfType(JBTabsImpl.class, owner.getParent());
    if (tabs != this) return null;
    return owner;
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

  private void fireSelectionChanged(@Nullable TabInfo oldInfo, @Nullable TabInfo newInfo) {
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


  private void fireTabRemoved(@NotNull TabInfo info) {
    for (TabsListener eachListener : myTabListeners) {
      if (eachListener != null) {
        eachListener.tabRemoved(info);
      }
    }
  }

  private @NotNull ActionCallback requestFocus(final Component toFocus, boolean inWindow) {
    if (toFocus == null) return ActionCallback.DONE;

    if (isShowing()) {
      ActionCallback res = new ActionCallback();
      ApplicationManager.getApplication().invokeLater(() -> {
        if (inWindow) {
          toFocus.requestFocusInWindow();
          res.setDone();
        }
        else {
          myFocusManager.requestFocusInProject(toFocus, myProject).notifyWhenDone(res);
        }
      });
      return res;
    }
    return ActionCallback.REJECTED;
  }

  private @NotNull ActionCallback removeDeferred() {
    if (myDeferredToRemove.isEmpty()) {
      return ActionCallback.DONE;
    }

    ActionCallback callback = new ActionCallback();
    long executionRequest = ++myRemoveDeferredRequest;
    myFocusManager.doWhenFocusSettlesDown(() -> {
      if (myRemoveDeferredRequest == executionRequest) {
        removeDeferredNow();
      }

      callback.setDone();
    });
    return callback;
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
      revalidateAndRepaint();
    }
    else if (TabInfo.ICON.equals(evt.getPropertyName())) {
      updateIcon(tabInfo);
      revalidateAndRepaint();
    }
    else if (TabInfo.TAB_COLOR.equals(evt.getPropertyName())) {
      revalidateAndRepaint();
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
    List<TabInfo> all = getTabs();
    for (TabInfo tabInfo : all) {
      TabLabel eachLabel = myInfo2Label.get(tabInfo);
      eachLabel.setTabEnabled(tabInfo.isEnabled());
    }

    TabInfo selected = getSelectedInfo();
    if (selected != null && !selected.isEnabled()) {
      TabInfo toSelect = getToSelectOnRemoveOf(selected);
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
        TabInfo toSelect = getToSelectOnRemoveOf(mySelectedInfo);
        setSelectedInfo(toSelect);
      }
      updateAll(true);
    }
  }

  private int getIndexInVisibleArray(TabInfo each) {
    Integer info = myHiddenInfos.get(each);
    int index = info == null ? myVisibleInfos.size() : info.intValue();

    if (index > myVisibleInfos.size()) {
      index = myVisibleInfos.size();
    }

    if (index < 0) {
      index = 0;
    }

    return index;
  }

  private void updateIcon(@NotNull TabInfo tabInfo) {
    myInfo2Label.get(tabInfo).setIcon(tabInfo.getIcon());
  }

  public void revalidateAndRepaint() {
    revalidateAndRepaint(true);
  }

  @Override
  public boolean isOpaque() {
    return super.isOpaque() && !myVisibleInfos.isEmpty();
  }

  protected void revalidateAndRepaint(boolean layoutNow) {
    if (myVisibleInfos.isEmpty() && getParent() != null) {
      Component nonOpaque = ComponentUtil.findUltimateParent(this);
      Rectangle toRepaint = SwingUtilities.convertRectangle(getParent(), getBounds(), nonOpaque);
      nonOpaque.repaint(toRepaint.x, toRepaint.y, toRepaint.width, toRepaint.height);
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

  private void updateText(@NotNull TabInfo tabInfo) {
    TabLabel label = myInfo2Label.get(tabInfo);
    label.setText(tabInfo.getColoredText());
    label.setToolTipText(tabInfo.getTooltipText());
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
  public @Nullable TabInfo getSelectedInfo() {
    if (myOldSelection != null) {
      return myOldSelection;
    }

    if (mySelectedInfo == null) {
      return myVisibleInfos.isEmpty() ? null : myVisibleInfos.get(0);
    }
    else if (myVisibleInfos.contains(mySelectedInfo)) {
      return mySelectedInfo;
    }
    else {
      setSelectedInfo(null);
      return null;
    }
  }

  private void setSelectedInfo(@Nullable TabInfo info) {
    mySelectedInfo = info;
    myInfo2Toolbar.forEach((tabInfo, toolbar) -> toolbar.setVisible(Objects.equals(info, tabInfo)));
  }

  @Override
  public @Nullable TabInfo getToSelectOnRemoveOf(TabInfo info) {
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
  TabInfo findEnabledForward(int from, boolean cycle) {
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
  TabInfo findEnabledBackward(int from, boolean cycle) {
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

  private Toolbar createToolbarComponent(final TabInfo tabInfo) {
    return new Toolbar(this, tabInfo);
  }

  @Override
  public @NotNull TabInfo getTabAt(final int tabIndex) {
    return getTabs().get(tabIndex);
  }

  @Override
  public @NotNull List<TabInfo> getTabs() {
    EDT.assertIsEdt();
    if (myAllTabs != null) {
      return myAllTabs;
    }

    List<TabInfo> result = new ArrayList<>(myVisibleInfos);
    for (TabInfo each : myHiddenInfos.keySet()) {
      result.add(getIndexInVisibleArray(each), each);
    }

    if (isAlphabeticalMode()) {
      sortTabsAlphabetically(result);
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

  @Override
  public @NotNull JBTabsPresentation setToDrawBorderIfTabsHidden(final boolean toDrawBorderIfTabsHidden) {
    return this;
  }

  @Override
  public @NotNull JBTabs getJBTabs() {
    return this;
  }

  public static final class Toolbar extends NonOpaquePanel {
    private final JBTabsImpl tabs;
    private final TabInfo info;

    public Toolbar(JBTabsImpl tabs, TabInfo info) {
      this.tabs = tabs;
      this.info = info;

      setLayout(new BorderLayout());

      final ActionGroup group = info.getGroup();
      final JComponent side = info.getSideComponent();

      if (group != null) {
        final String place = info.getPlace();
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
          place != null && !place.equals(ActionPlaces.UNKNOWN) ? place : "JBTabs", group, tabs.myHorizontalSide);
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
      UIUtil.uiTraverser(this).filter(c -> !UIUtil.canDisplayFocusedState(c)).forEach(c -> c.setFocusable(false));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension base = super.getPreferredSize();
      TabLabel label = tabs.myInfo2Label.get(info);
      if (tabs.myHorizontalSide && tabs.mySideComponentOnTabs && label != null && base.height > 0) {
        return new Dimension(base.width, label.getPreferredSize().height);
      }
      return base;
    }

    public boolean isEmpty() {
      return getComponentCount() == 0;
    }
  }

  private void updateScrollBarModel() {
    BoundedRangeModel scrollBarModel = getScrollBarModel();
    if (scrollBarModel.getValueIsAdjusting()) return;

    int maximum = myLastLayoutPass.getRequiredLength();
    int value = myLayout.getScrollOffset();
    int extent;

    if (isHorizontalTabs()) {
      extent = getTabsAreaWidth();
    }
    else {
      extent = getHeight();
      if (!ExperimentalUI.isNewUI() && myEntryPointToolbar != null && myEntryPointToolbar.getComponent().isVisible()) {
        extent = myEntryPointToolbar.getComponent().getY();
      }
    }

    scrollBarModel.setMaximum(maximum);
    scrollBarModel.setValue(value);
    // If extent is 0, that means the layout is in improper state, so we don't show the scrollbar.
    scrollBarModel.setExtent(extent == 0 ? value + maximum : extent);
  }

  private void updateTabsOffsetFromScrollBar() {
    int currentUnitsOffset = myLayout.getScrollOffset();
    int updatedOffset = getScrollBarModel().getValue();
    myLayout.scroll(updatedOffset - currentUnitsOffset);
    relayout(false, false);
  }

  @Override
  public void doLayout() {
    try {

      final Collection<TabLabel> labels = myInfo2Label.values();
      for (TabLabel each : labels) {
        each.setTabActionsAutoHide(myTabLabelActionsAutoHide);
      }

      Rectangle moreBoundsBeforeLayout = myMoreToolbar.getComponent().getBounds();
      Rectangle entryPointBoundsBeforeLayout = myEntryPointToolbar != null
                                               ? myEntryPointToolbar.getComponent().getBounds()
                                               : new Rectangle(0, 0, 0, 0);

      myHeaderFitSize = computeHeaderFitSize();

      List<TabInfo> visible = new ArrayList<>(getVisibleInfos());

      if (myDropInfo != null && !visible.contains(myDropInfo) && myShowDropLocation) {
        if (getDropInfoIndex() >= 0 && getDropInfoIndex() < visible.size()) {
          visible.add(getDropInfoIndex(), myDropInfo);
        }
        else {
          visible.add(myDropInfo);
        }
      }

      if (myLayout instanceof SingleRowLayout) {
        mySingleRowLayout.scrollSelectionInView();
        myLastLayoutPass = mySingleRowLayout.layoutSingleRow(visible);

        Rectangle titleRect = getTitleRect();
        if (titleRect != null && !titleRect.isEmpty()) {
          Dimension preferredSize = myTitleWrapper.getPreferredSize();
          Rectangle bounds = new Rectangle(titleRect);
          JBInsets.removeFrom(bounds, getLayoutInsets());
          int xDiff = (bounds.width - preferredSize.width) / 2;
          int yDiff = (bounds.height - preferredSize.height) / 2;
          bounds.x += xDiff;
          bounds.width -= 2 * xDiff;
          bounds.y += yDiff;
          bounds.height -= 2 * yDiff;
          myTitleWrapper.setBounds(bounds);
        }
        else {
          myTitleWrapper.setBounds(new Rectangle());
        }
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
        myTableLayout.scrollSelectionInView();
        myLastLayoutPass = myTableLayout.layoutTable(visible);
        mySingleRowLayout.myLastSingRowLayout = null;
      }

      centerizeEntryPointToolbarPosition();
      centerizeMoreToolbarPosition();

      moveDraggedTabLabel();

      myTabActionsAutoHideListener.processMouseOver();

      applyResetComponents();

      myScrollBar.setOrientation(isHorizontalTabs() ? Adjustable.HORIZONTAL : Adjustable.VERTICAL);
      myScrollBar.setBounds(getScrollBarBounds());
      updateScrollBarModel();

      updateToolbarIfVisibilityChanged(myMoreToolbar, moreBoundsBeforeLayout);
      updateToolbarIfVisibilityChanged(myEntryPointToolbar, entryPointBoundsBeforeLayout);
    }
    finally {
      myForcedRelayout = false;
    }
  }

  private static void updateToolbarIfVisibilityChanged(@Nullable ActionToolbar toolbar, @NotNull Rectangle previousBounds) {
    if (toolbar == null) return;
    Rectangle curBounds = toolbar.getComponent().getBounds();
    if (curBounds.isEmpty() != previousBounds.isEmpty()) {
      toolbar.updateActionsImmediately();
    }
  }

  private int getTabsAreaWidth() {
    if (myMoreToolbar.getComponent().isVisible()) {
      return myMoreToolbar.getComponent().getX();
    }
    else if (myEntryPointToolbar != null && myEntryPointToolbar.getComponent().isVisible() && myTableLayout.myLastTableLayout == null) {
      return myEntryPointToolbar.getComponent().getX();
    }
    return getBounds().width;
  }

  private void centerizeMoreToolbarPosition() {
    Rectangle moreRect = getMoreRect();
    JComponent mComponent = myMoreToolbar.getComponent();
    if (moreRect != null && !moreRect.isEmpty()) {
      Rectangle bounds = new Rectangle(moreRect);
      if (!ExperimentalUI.isNewUI() || !getTabsPosition().isSide()) {
        Dimension preferredSize = mComponent.getPreferredSize();
        int xDiff = (bounds.width - preferredSize.width) / 2;
        int yDiff = (bounds.height - preferredSize.height) / 2;
        bounds.x += xDiff + 2;
        bounds.width -= 2 * xDiff;
        bounds.y += yDiff;
        bounds.height -= 2 * yDiff;
      }
      mComponent.setBounds(bounds);
    }
    else {
      mComponent.setBounds(new Rectangle());
    }
    mComponent.putClientProperty(LAYOUT_DONE, true);
  }

  private void centerizeEntryPointToolbarPosition() {
    JComponent eComponent = myEntryPointToolbar == null ? null : myEntryPointToolbar.getComponent();
    if (eComponent == null) {
      return;
    }
    Rectangle entryPointRect = getEntryPointRect();
    if (entryPointRect != null && !entryPointRect.isEmpty() && getTabCount() > 0) {
      Dimension preferredSize = eComponent.getPreferredSize();
      Rectangle bounds = new Rectangle(entryPointRect);
      if (!ExperimentalUI.isNewUI() || !getTabsPosition().isSide()) {
        int xDiff = (bounds.width - preferredSize.width) / 2;
        int yDiff = (bounds.height - preferredSize.height) / 2;
        bounds.x += xDiff + 2;
        bounds.width -= 2 * xDiff;
        bounds.y += yDiff;
        bounds.height -= 2 * yDiff;
      }
      eComponent.setBounds(bounds);
    }
    else {
      eComponent.setBounds(new Rectangle());
    }
    eComponent.putClientProperty(LAYOUT_DONE, true);
  }

  void moveDraggedTabLabel() {
    if (myDragHelper != null && myDragHelper.myDragRec != null) {
      final TabLabel selectedLabel = myInfo2Label.get(getDraggedTabSelectionInfo());
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

  protected @Nullable TabInfo getDraggedTabSelectionInfo() {
    return getSelectedInfo();
  }

  private Dimension computeHeaderFitSize() {
    final Max max = computeMaxSize();

    if (myPosition == JBTabsPosition.top || myPosition == JBTabsPosition.bottom) {
      return new Dimension(getSize().width, myHorizontalSide ? Math.max(max.myLabel.height, max.myToolbar.height) : max.myLabel.height);
    }
    return new Dimension(max.myLabel.width + (myHorizontalSide ? 0 : max.myToolbar.width), getSize().height);
  }

  public Rectangle layoutComp(int componentX, int componentY, final JComponent comp, int deltaWidth, int deltaHeight) {
    return layoutComp(new Rectangle(componentX, componentY, getWidth(), getHeight()), comp, deltaWidth, deltaHeight);
  }

  public Rectangle layoutComp(final Rectangle bounds, final JComponent comp, int deltaWidth, int deltaHeight) {
    Insets insets = getLayoutInsets();
    Insets inner = getInnerInsets();

    int x = insets.left + bounds.x + inner.left;
    int y = insets.top + bounds.y + inner.top;
    int width = bounds.width - insets.left - insets.right - bounds.x - inner.left - inner.right;
    int height = bounds.height - insets.top - insets.bottom - bounds.y - inner.top - inner.bottom;

    if (!isHideTabs()) {
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

  public final @NotNull JBTabsPosition getPosition() {
    return myPosition;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myVisibleInfos.isEmpty()) {
      if (myEmptyText != null) {
        UISettings.setupAntialiasing(g);
        UIUtil.drawCenteredString((Graphics2D)g, new Rectangle(0, 0, getWidth(), getHeight()), myEmptyText);
      }
      return;
    }

    myTabPainter.fillBackground((Graphics2D)g, new Rectangle(0, 0, getWidth(), getHeight()));
    drawBorder(g);
    drawToolbarSeparator(g);
  }

  private void drawToolbarSeparator(Graphics g) {
    Toolbar toolbar = myInfo2Toolbar.get(getSelectedInfo());
    if (toolbar != null && toolbar.getParent() == this && !mySideComponentOnTabs && !myHorizontalSide && isHideTabs()) {
      Rectangle bounds = toolbar.getBounds();
      if (bounds.width > 0) {
        if (mySideComponentBefore) {
          getTabPainter().paintBorderLine((Graphics2D)g, mySeparatorWidth,
                                          new Point(bounds.x + bounds.width, bounds.y),
                                          new Point(bounds.x + bounds.width, bounds.y + bounds.height));
        }
        else {
          getTabPainter().paintBorderLine((Graphics2D)g, mySeparatorWidth,
                                          new Point(bounds.x - mySeparatorWidth, bounds.y),
                                          new Point(bounds.x - mySeparatorWidth, bounds.y + bounds.height));
        }
      }
    }
  }

  protected TabLabel getSelectedLabel() {
    return myInfo2Label.get(getSelectedInfo());
  }

  protected List<TabInfo> getVisibleInfos() {
    if (AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      groupPinnedFirst(myVisibleInfos);
    }
    if (isAlphabeticalMode()) {
      var result = new ArrayList<>(myVisibleInfos);
      sortTabsAlphabetically(result);
      return result;
    }
    return myVisibleInfos;
  }

  private static void sortTabsAlphabetically(@NotNull List<TabInfo> tabs) {
    int lastPinnedIndex = ContainerUtil.lastIndexOf(tabs, TabInfo::isPinned);
    if (lastPinnedIndex == -1 || !AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      tabs.sort(ABC_COMPARATOR);
    }
    else {
      tabs.subList(0, lastPinnedIndex + 1).sort(ABC_COMPARATOR);
      tabs.subList(lastPinnedIndex + 1, tabs.size()).sort(ABC_COMPARATOR);
    }
  }

  private void groupPinnedFirst(List<TabInfo> infos) {
    int firstNotPinned = -1;
    boolean changed = false;
    for (int i = 0; i < infos.size(); i++) {
      TabInfo info = infos.get(i);
      if (info.isPinned()) {
        if (firstNotPinned != -1) {
          TabInfo tabInfo = infos.remove(firstNotPinned);
          infos.add(firstNotPinned, info);
          infos.set(i, tabInfo);
          firstNotPinned++;
          changed = true;
        }
      }
      else if (firstNotPinned == -1) {
        firstNotPinned = i;
      }
    }

    if (changed) resetTabsCache();
  }

  protected LayoutPassInfo getLastLayoutPass() {
    return myLastLayoutPass;
  }

  public static int getSelectionTabVShift() {
    return 2;
  }

  private boolean isNavigationVisible() {
    return myVisibleInfos.size() > 1;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  protected void drawBorder(Graphics g) {
    if (!isHideTabs()) {
      myBorder.paintBorder(this, g, 0, 0, getWidth(), getHeight());
    }
  }

  private Max computeMaxSize() {
    Max max = new Max();
    final boolean isSideComponentOnTabs = myLayout.isSideComponentOnTabs();

    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      max.myLabel.height = Math.max(max.myLabel.height, label.getPreferredSize().height);
      max.myLabel.width = Math.max(max.myLabel.width, label.getPreferredSize().width);

      if (isSideComponentOnTabs) {
        final Toolbar toolbar = myInfo2Toolbar.get(eachInfo);
        if (toolbar != null && !toolbar.isEmpty()) {
          max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.getPreferredSize().height);
          max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.getPreferredSize().width);
        }
      }
    }
    if (getTabsPosition().isSide()) {
      if (mySplitter.getSideTabsLimit() > 0) {
        max.myLabel.width = Math.min(max.myLabel.width, mySplitter.getSideTabsLimit());
      }
    }

    return max;
  }

  @Override
  public Dimension getMinimumSize() {
    return computeSize(component -> component.getMinimumSize(), 1);
  }

  @Override
  public Dimension getPreferredSize() {
    return computeSize(component -> component.getPreferredSize(), 3);
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
  public @NotNull JBTabsPresentation getPresentation() {
    return this;
  }

  @Override
  public @NotNull ActionCallback removeTab(final TabInfo info) {
    return doRemoveTab(info, null, false);
  }

  @Override
  public void removeTab(final TabInfo info, @Nullable TabInfo forcedSelectionTransfer) {
    doRemoveTab(info, forcedSelectionTransfer, false);
  }

  @RequiresEdt
  private @NotNull ActionCallback doRemoveTab(TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean isDropTarget) {
    if (myRemoveNotifyInProgress) {
      LOG.warn(new IllegalStateException("removeNotify in progress"));
    }
    if (myPopupInfo == info) myPopupInfo = null;

    if (!isDropTarget) {
      if (info == null || !getTabs().contains(info)) return ActionCallback.DONE;
    }

    if (isDropTarget && myLastLayoutPass != null) {
      myLastLayoutPass.myVisibleInfos.remove(info);
    }

    ActionCallback result = new ActionCallback();

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
      boolean transferFocus = isFocused(info);
      processRemove(info, false);
      if (clearSelection) {
        setSelectedInfo(info);
      }
      _setSelected(toSelect, transferFocus, true).doWhenProcessed(() -> removeDeferred().notifyWhenDone(result));
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

  // Tells whether focus is currently within one of the tab's components, or it was there last time the containing window had focus
  private boolean isFocused(@NotNull TabInfo info) {
    TabLabel label = myInfo2Label.get(info);
    Toolbar toolbar = myInfo2Toolbar.get(info);
    JComponent component = info.getComponent();

    Predicate<Component> ancestorChecker = focusOwner -> {
      while (focusOwner != null) {
        if (focusOwner == label || focusOwner == toolbar || focusOwner == component) {
          return true;
        }
        focusOwner = focusOwner.getParent();
      }
      return false;
    };

    if (ancestorChecker.test(KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner())) {
      return true;
    }
    Window ourWindow = SwingUtilities.getWindowAncestor(this);
    return ourWindow != null && !ourWindow.isFocused() && ancestorChecker.test(ourWindow.getMostRecentFocusOwner());
  }

  private void processRemove(final TabInfo info, boolean forcedNow) {
    TabLabel tabLabel = myInfo2Label.get(info);
    if (tabLabel != null) {
      remove(tabLabel);
    }

    Toolbar toolbar = myInfo2Toolbar.get(info);
    if (toolbar != null) {
      remove(toolbar);
    }

    JComponent tabComponent = info.getComponent();
    if (forcedNow || !isToDeferRemoveForLater(tabComponent)) {
      remove(tabComponent);
    }
    else {
      addToDeferredRemove(tabComponent);
    }

    myVisibleInfos.remove(info);
    myHiddenInfos.remove(info);
    myInfo2Label.remove(info);
    myInfo2Page.remove(info);
    myInfo2Toolbar.remove(info);
    if (tabLabelAtMouse == tabLabel) {
      tabLabelAtMouse = null;
    }
    resetTabsCache();

    updateAll(false);
  }

  @Override
  public @Nullable TabInfo findInfo(Component component) {
    for (TabInfo each : getTabs()) {
      if (each.getComponent() == component) {
        return each;
      }
    }
    return null;
  }

  @Override
  public TabInfo findInfo(MouseEvent event) {
    Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), this);
    return _findInfo(point, false);
  }

  @Override
  public TabInfo findInfo(Object object) {
    for (int i = 0; i < getTabCount(); i++) {
      TabInfo each = getTabAt(i);
      Object eachObject = each.getObject();
      if (eachObject != null && eachObject.equals(object)) {
        return each;
      }
    }
    return null;
  }

  private @Nullable TabInfo _findInfo(Point point, boolean labelsOnly) {
    Component component = findComponentAt(point);
    while (component != this) {
      if (component == null) return null;
      if (component instanceof TabLabel) {
        return ((TabLabel)component).getInfo();
      }
      if (!labelsOnly) {
        final TabInfo info = findInfo(component);
        if (info != null) return info;
      }
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

  private static final class Max {
    final Dimension myLabel = new Dimension();
    final Dimension myToolbar = new Dimension();
  }

  private void updateContainer(boolean forced, boolean layoutNow) {
    if (myProject != null && !myProject.isOpen() && !myProject.isDefault()) {
      return;
    }

    for (TabInfo tabInfo : List.copyOf(myVisibleInfos)) {
      JComponent component = tabInfo.getComponent();
      if (tabInfo == getSelectedInfo()) {
        unqueueFromRemove(component);

        Container parent = component.getParent();
        if (parent != null && parent != this) {
          parent.remove(component);
        }

        if (component.getParent() == null) {
          add(component);
        }
      }
      else {
        if (component.getParent() == null) {
          continue;
        }
        if (isToDeferRemoveForLater(component)) {
          addToDeferredRemove(component);
        }
        else {
          remove(component);
        }
      }
    }

    updateEntryPointToolbar();

    if (myLayout == mySingleRowLayout) {
      mySingleRowLayout.scrollSelectionInView();
    }
    else if (myLayout == myTableLayout) {
      myTableLayout.scrollSelectionInView();
    }
    relayout(forced, layoutNow);
  }

  private void updateEntryPointToolbar() {
    if (myEntryPointToolbar != null) {
      remove(myEntryPointToolbar.getComponent());
    }

    TabInfo selectedInfo = getSelectedInfo();
    ActionGroup tabActionGroup = selectedInfo != null ? selectedInfo.getTabPaneActions() : null;
    DefaultActionGroup entryPointActionGroup = getEntryPointActionGroup();
    if (tabActionGroup != null || entryPointActionGroup != null) {
      ActionGroup group;
      if (tabActionGroup != null && entryPointActionGroup != null) {
        if (!myMoreToolbar.getComponent().getBounds().isEmpty()
            && tabActionGroup.getChildren(null).length != 0
            // check that more toolbar and entry point toolbar are in the same row
            && (!TabLayout.showPinnedTabsSeparately() || ContainerUtil.all(getTabs(), info -> !info.isPinned()))) {
          group = new DefaultActionGroup(new FakeEmptyAction(), Separator.create(), tabActionGroup, Separator.create(), entryPointActionGroup);
        }
        else {
          group = new DefaultActionGroup(tabActionGroup, Separator.create(), entryPointActionGroup);
        }
      }
      else {
        group = tabActionGroup != null ? tabActionGroup : entryPointActionGroup;
      }

      if (myEntryPointToolbar != null && myEntryPointToolbar.getActionGroup().equals(group)) {
        // keep old toolbar to avoid blinking (actions need to be updated to be visible)
        add(myEntryPointToolbar.getComponent());
      }
      else {
        myEntryPointToolbar = createToolbar(group);
        add(myEntryPointToolbar.getComponent());
      }
    }
    else {
      myEntryPointToolbar = null;
    }
  }

  /**
   * @return insets, that should be used to layout {@link JBTabsImpl#myMoreToolbar} and {@link JBTabsImpl#myEntryPointToolbar}
   */
  public Insets getActionsInsets() {
    if (ExperimentalUI.isNewUI()) {
      return getPosition().isSide() ? JBInsets.create(new Insets(4, 8, 4, 3))
                                    : JBInsets.create(new Insets(0, 5, 0, 8));
    }
    else {
      return JBInsets.create(new Insets(0, 1, 0, 1));
    }
  }

  // Useful when you need to always show separator as first or last component of ActionToolbar
  // Just put it as first or last action and any separator will not be counted as corner and will be shown
  private static class FakeEmptyAction extends DumbAwareAction implements CustomComponentAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // do nothing
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      JPanel panel = new JPanel();
      Dimension size = new Dimension(0, 0);
      panel.setPreferredSize(size);
      return panel;
    }
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
    if (myMoreToolbar != null) {
      myMoreToolbar.getComponent().setVisible(!isHideTabs() &&
                                              (getEffectiveLayout() instanceof ScrollableSingleRowLayout ||
                                               getEffectiveLayout() instanceof TableLayout));
    }
    revalidateAndRepaint(layoutNow);
  }

  public int getBorderThickness() {
    return myBorder.getThickness();
  }

  @Override
  public @NotNull JBTabs addTabMouseListener(@NotNull MouseListener listener) {
    removeListeners();
    myTabMouseListeners.add(listener);
    addListeners();
    return this;
  }

  @Override
  public @NotNull JComponent getComponent() {
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
    return addListener(listener, null);
  }

  @Override
  public JBTabs addListener(@NotNull TabsListener listener, @Nullable Disposable disposable) {
    myTabListeners.add(listener);
    if (disposable != null) {
      Disposer.register(disposable, () -> myTabListeners.remove(listener));
    }
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
  public int getIndexOf(final @Nullable TabInfo tabInfo) {
    return getVisibleInfos().indexOf(tabInfo);
  }

  @Override
  public boolean isHideTabs() {
    return myHideTabs || myHideTopPanel;
  }

  @Override
  public void setHideTabs(final boolean hideTabs) {
    if (isHideTabs() == hideTabs) return;

    myHideTabs = hideTabs;

    if (myEntryPointToolbar != null) {
      myEntryPointToolbar.getComponent().setVisible(!myHideTabs);
    }

    relayout(true, false);
  }

  /**
   * @param hideTopPanel true if tabs and top toolbar should be hidden from a view
   */
  @Override
  public void setHideTopPanel(boolean hideTopPanel) {
    if (isHideTopPanel() == hideTopPanel) return;

    myHideTopPanel = hideTopPanel;

    getTabs().stream()
      .map(TabInfo::getSideComponent)
      .forEach(component -> component.setVisible(!myHideTopPanel));

    relayout(true, true);
  }

  @Override
  public boolean isHideTopPanel() {
    return myHideTopPanel;
  }

  @Override
  public @NotNull JBTabsPresentation setActiveTabFillIn(final @Nullable Color color) {
    if (!isChanged(myActiveTabFillIn, color)) return this;

    myActiveTabFillIn = color;
    revalidateAndRepaint(false);
    return this;
  }

  private static boolean isChanged(Object oldObject, Object newObject) {
    return !Comparing.equal(oldObject, newObject);
  }

  @Override
  public @NotNull JBTabsPresentation setTabLabelActionsAutoHide(boolean autoHide) {
    if (myTabLabelActionsAutoHide != autoHide) {
      myTabLabelActionsAutoHide = autoHide;
      revalidateAndRepaint(false);
    }
    return this;
  }

  @Override
  public JBTabsPresentation setFocusCycle(boolean root) {
    setFocusCycleRoot(root);
    return this;
  }


  @Override
  public JBTabsPresentation setPaintFocus(boolean paintFocus) {
    myPaintFocus = paintFocus;
    return this;
  }

  private abstract static class BaseNavigationAction extends DumbAwareAction {
    private final ShadowAction myShadow;
    private final JBTabsImpl myTabs;

    BaseNavigationAction(@NlsSafe @NotNull String copyFromId, @NotNull JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
      myTabs = tabs;
      myShadow = new ShadowAction(this, copyFromId, tabs, parentDisposable);
      setEnabledInModalContext(true);
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
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
    JBTabsImpl findNavigatableTabs(JBTabsImpl tabs) {
      // The debugger UI contains multiple nested JBTabsImpl, where the innermost JBTabsImpl has only one tab. In this case,
      // the action should target the outer JBTabsImpl.
      if (tabs == null || tabs != myTabs) {
        return null;
      }
      if (tabs.isNavigatable()) {
        return tabs;
      }
      Component c = tabs.getParent();
      while (c != null) {
        if (c instanceof JBTabsImpl && ((JBTabsImpl)c).isNavigatable()) {
          return (JBTabsImpl)c;
        }
        c = c.getParent();
      }
      return null;
    }

    public void reconnect(String actionId) {
      myShadow.reconnect(ActionManager.getInstance().getAction(actionId));
    }

    protected abstract void _update(AnActionEvent e, JBTabsImpl tabs, int selectedIndex);

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      JBTabsImpl tabs = (JBTabsImpl)e.getData(NAVIGATION_ACTIONS_KEY);
      tabs = findNavigatableTabs(tabs);
      if (tabs == null) {
        return;
      }

      List<TabInfo> infos;
      int index;
      while (true) {
        infos = tabs.getVisibleInfos();
        index = infos.indexOf(tabs.getSelectedInfo());
        if (index == -1) return;
        if (borderIndex(infos, index) && tabs.navigatableParent() != null) {
          tabs = tabs.navigatableParent();
        }
        else {
          break;
        }
      }

      _actionPerformed(e, tabs, index);
    }

    protected abstract boolean borderIndex(List<TabInfo> infos, int index);

    protected abstract void _actionPerformed(AnActionEvent e, JBTabsImpl tabs, int selectedIndex);
  }

  private static final class SelectNextAction extends BaseNavigationAction {
    private SelectNextAction(JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
      super(IdeActions.ACTION_NEXT_TAB, tabs, parentDisposable);
    }

    @Override
    protected void _update(AnActionEvent e, JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledForward(selectedIndex, true) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    protected boolean borderIndex(List<TabInfo> infos, int index) {
      return index == infos.size() - 1;
    }

    @Override
    protected void _actionPerformed(AnActionEvent e, JBTabsImpl tabs, int selectedIndex) {
      TabInfo tabInfo = tabs.findEnabledForward(selectedIndex, true);
      if (tabInfo != null) {
        JComponent lastFocus = tabInfo.getLastFocusOwner();
        tabs.select(tabInfo, true);
        tabs.myNestedTabs.stream()
          .filter((nestedTabs) -> (lastFocus == null) || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs))
          .forEach((nestedTabs) -> {
            nestedTabs.selectFirstVisible();
          });
      }
    }
  }

  protected boolean isNavigatable() {
    final int selectedIndex = getVisibleInfos().indexOf(getSelectedInfo());
    return isNavigationVisible() && selectedIndex >= 0 && myNavigationActionsEnabled;
  }

  private JBTabsImpl navigatableParent() {
    Component c = getParent();
    while (c != null) {
      if (c instanceof JBTabsImpl && ((JBTabsImpl)c).isNavigatable()) {
        return (JBTabsImpl)c;
      }
      c = c.getParent();
    }

    return null;
  }

  private void selectFirstVisible() {
    if (!isNavigatable()) return;
    TabInfo select = getVisibleInfos().get(0);
    JComponent lastFocus = select.getLastFocusOwner();
    select(select, true);
    myNestedTabs.stream()
      .filter((nestedTabs) -> (lastFocus == null) || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs))
      .forEach((nestedTabs) -> {
        nestedTabs.selectFirstVisible();
      });
  }

  private void selectLastVisible() {
    if (!isNavigatable()) return;
    int last = getVisibleInfos().size() - 1;
    TabInfo select = getVisibleInfos().get(last);
    JComponent lastFocus = select.getLastFocusOwner();
    select(select, true);
    myNestedTabs.stream()
      .filter((nestedTabs) -> (lastFocus == null) || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs))
      .forEach((nestedTabs) -> {
        nestedTabs.selectLastVisible();
      });
  }

  private static final class SelectPreviousAction extends BaseNavigationAction {
    private SelectPreviousAction(JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
      super(IdeActions.ACTION_PREVIOUS_TAB, tabs, parentDisposable);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    protected void _update(final AnActionEvent e, final JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledBackward(selectedIndex, true) != null);
    }

    @Override
    protected boolean borderIndex(List<TabInfo> infos, int index) {
      return index == 0;
    }

    @Override
    protected void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex) {
      TabInfo tabInfo = tabs.findEnabledBackward(selectedIndex, true);
      if (tabInfo != null) {
        JComponent lastFocus = tabInfo.getLastFocusOwner();
        tabs.select(tabInfo, true);
        tabs.myNestedTabs.stream()
          .filter((nestedTabs) -> (lastFocus == null) || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs))
          .forEach((nestedTabs) -> {
            nestedTabs.selectLastVisible();
          });
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
    mySingleRow = singleRow;
    updateRowLayout();
    return this;
  }

  private boolean setLayout(TabLayout layout) {
    if (myLayout == layout) return false;
    myLayout = layout;
    return true;
  }

  public int getSeparatorWidth() {
    return mySeparatorWidth;
  }

  public boolean useSmallLabels() {
    return false;
  }

  @Override
  public boolean isSingleRow() {
    return mySingleRow;
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
    return myLayout;
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

  protected void adjust(final TabInfo each) {
    if (myAdjustBorders) {
      UIUtil.removeScrollBorder(each.getComponent());
    }
  }

  @Override
  public void sortTabs(Comparator<? super TabInfo> comparator) {
    myVisibleInfos.sort(comparator);
    resetTabsCache();
    relayout(true, false);
  }

  protected void reorderTab(@NotNull TabInfo tabInfo, int newIndex) {
    if (myVisibleInfos.remove(tabInfo)) {
      myVisibleInfos.add(newIndex, tabInfo);
      resetTabsCache();
      relayout(true, false);
    }
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
  public @Nullable Object getData(final @NotNull @NonNls String dataId) {
    if (myDataProvider != null) {
      final Object value = myDataProvider.getData(dataId);
      if (value != null) return value;
    }

    if (QuickActionProvider.KEY.is(dataId)) {
      return this;
    }
    if (MorePopupAware.KEY.is(dataId)) {
      return this;
    }

    return NAVIGATION_ACTIONS_KEY.is(dataId) ? this : null;
  }

  @Override
  public @NotNull List<AnAction> getActions(boolean originalProvider) {
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

  public ActionGroup getNavigationActions() {
    return myNavigationActions;
  }

  @Override
  public DataProvider getDataProvider() {
    return myDataProvider;
  }

  @Override
  public JBTabsImpl setDataProvider(final @NotNull DataProvider dataProvider) {
    myDataProvider = dataProvider;
    return this;
  }


  static boolean isSelectionClick(MouseEvent e) {
    if (e.getClickCount() == 1) {
      if (!e.isPopupTrigger()) {
        return e.getButton() == MouseEvent.BUTTON1 && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown();
      }
    }
    return false;
  }

  private static final class DefaultDecorator implements UiDecorator {
    @Override
    public @NotNull UiDecoration getDecoration() {
      return new UiDecoration(null, new JBInsets(5, 12, 5, 12));
    }
  }

  public Rectangle layout(JComponent c, Rectangle bounds) {
    final Rectangle now = c.getBounds();
    if (!bounds.equals(now)) {
      c.setBounds(bounds);
    }
    c.doLayout();
    c.putClientProperty(LAYOUT_DONE, Boolean.TRUE);

    return bounds;
  }

  public Rectangle layout(JComponent c, int x, int y, int width, int height) {
    return layout(c, new Rectangle(x, y, width, height));
  }

  public static void resetLayout(JComponent c) {
    if (c == null) return;
    c.putClientProperty(LAYOUT_DONE, null);
  }

  private void applyResetComponents() {
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      if (each instanceof JComponent jc) {
        if (!ClientProperty.isTrue(jc, LAYOUT_DONE)) {
          layout(jc, new Rectangle(0, 0, 0, 0));
        }
      }
    }
  }


  @Override
  public @NotNull JBTabsPresentation setTabLabelActionsMouseDeadzone(final TimedDeadzone.Length length) {
    myTabActionsMouseDeadzone = length;
    final List<TabInfo> all = getTabs();
    for (TabInfo each : all) {
      final TabLabel eachLabel = myInfo2Label.get(each);
      eachLabel.updateTabActions();
    }
    return this;
  }

  @Override
  public @NotNull JBTabsPresentation setTabsPosition(final JBTabsPosition position) {
    myPosition = position;
    OnePixelDivider divider = mySplitter.getDivider();
    if (position.isSide() && divider.getParent() == null) {
      add(divider);
    }
    else if (divider.getParent() == this && !position.isSide()) {
      remove(divider);
    }

    applyDecoration();
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
    return myTabDraggingEnabled;
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
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
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
      setDropSide(-1);
      doRemoveTab(dropInfo, null, true);
    }
  }

  @Override
  public Image startDropOver(TabInfo tabInfo, RelativePoint point) {
    myDropInfo = tabInfo;

    Point pointInMySpace = point.getPoint(this);
    int index = myLayout.getDropIndexFor(pointInMySpace);
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
    Point pointInMySpace = point.getPoint(this);
    int index = myLayout.getDropIndexFor(pointInMySpace);
    int side;
    if (myVisibleInfos.isEmpty()) {
      side = SwingConstants.CENTER;
    }
    else {
      side = index != -1
             ? -1
             : myLayout.getDropSideFor(pointInMySpace);
    }
    if (index != getDropInfoIndex()) {
      setDropInfoIndex(index);
      relayout(true, false);
    }
    if (side != myDropSide) {
      setDropSide(side);
      relayout(true, false);
    }
  }

  @Override
  public int getDropInfoIndex() {
    return myDropInfoIndex;
  }

  protected DragHelper getDragHelper() {
    return myDragHelper;
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT,
    -1})
  public int getDropSide() {
    return myDropSide;
  }

  @Override
  public boolean isEmptyVisible() {
    return myVisibleInfos.isEmpty();
  }

  public int getTabHGap() {
    return -myBorder.getThickness();
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

    AccessibleJBTabsImpl() {
      getAccessibleComponent();
      addListener(new TabsListener() {
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
      if (accessibleChild instanceof TabLabel label) {
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
      return getSelectedInfo() == null ? 0 : 1;
    }

    @Override
    public Accessible getAccessibleSelection(int i) {
      if (getSelectedInfo() == null) {
        return null;
      }
      return myInfo2Page.get(getSelectedInfo());
    }

    @Override
    public boolean isAccessibleChildSelected(int i) {
      return i == getIndexOf(getSelectedInfo());
    }

    @Override
    public void addAccessibleSelection(int i) {
      TabInfo info = getTabAt(i);
      select(info, false);
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
   * <p>
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
      return getIndexOf(myTabInfo);
    }

    private TabLabel getTabLabel() {
      return myInfo2Label.get(getTabInfo());
    }

    /*
     * initializes the AccessibleContext for the page
     */
    void initAccessibleContext() {
      // Note: null checks because we do not want to load Accessibility classes unnecessarily.
      if (accessibleContext != null && myComponent instanceof Accessible) {
        AccessibleContext ac = myComponent.getAccessibleContext();
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
      AccessibleStateSet states = myParent.getAccessibleContext().getAccessibleStateSet();
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
      return getSelectedInfo() == getTabInfo() && myComponent instanceof Accessible ? 1 : 0;
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      return getSelectedInfo() == getTabInfo() && myComponent instanceof Accessible ? (Accessible)myComponent : null;
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
      return myComponent instanceof Accessible ? (Accessible)myComponent : null;
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
    public AccessibleIcon[] getAccessibleIcon() {
      AccessibleIcon accessibleIcon = null;

      if (getTabInfo().getIcon() instanceof ImageIcon) {
        AccessibleContext ac =
          ((ImageIcon)getTabInfo().getIcon()).getAccessibleContext();
        accessibleIcon = (AccessibleIcon)ac;
      }

      if (accessibleIcon != null) {
        AccessibleIcon[] returnIcons = new AccessibleIcon[1];
        returnIcons[0] = accessibleIcon;
        return returnIcons;
      }
      else {
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
      if (i != 0) {
        return null;
      }

      return "Activate";
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i != 0) {
        return false;
      }

      select(getTabInfo(), true);
      return true;
    }
  }

  /**
   * @deprecated Not used.
   */
  @Deprecated(forRemoval = true)
  public void dispose() {
  }

  private final class TitleAction extends AnAction implements CustomComponentAction {
    private final @NotNull Producer<? extends Pair<Icon, String>> myTitleProvider;
    private final JLabel myLabel = new JLabel() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT);
        return size;
      }

      @Override
      public void updateUI() {
        super.updateUI();
        setFont(new TabLabel(JBTabsImpl.this, new TabInfo(null)).getLabelComponent().getFont());
        setBorder(JBUI.Borders.empty(0, 5, 0, 6));
      }
    };

    private TitleAction(@NotNull Producer<? extends Pair<Icon, String>> titleProvider) {
      myTitleProvider = titleProvider;
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      update();
      return myLabel;
    }

    private void update() {
      Pair<Icon, String> pair = myTitleProvider.produce();
      myLabel.setIcon(pair.first);
      //noinspection HardCodedStringLiteral
      myLabel.setText(pair.second);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      //do nothing
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      update();
    }
  }
}
