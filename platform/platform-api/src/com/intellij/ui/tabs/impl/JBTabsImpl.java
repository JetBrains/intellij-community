/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowPassInfo;
import com.intellij.ui.tabs.impl.table.TableLayout;
import com.intellij.ui.tabs.impl.table.TablePassInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.LazyUiDisposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class JBTabsImpl extends JComponent
  implements JBTabs, PropertyChangeListener, TimerListener, DataProvider, PopupMenuListener, Disposable, JBTabsPresentation, Queryable,
             QuickActionProvider {

  public static final DataKey<JBTabsImpl> NAVIGATION_ACTIONS_KEY = DataKey.create("JBTabs");

  public static final Color MAC_AQUA_BG_COLOR = Gray._200;

  final ActionManager myActionManager;
  private final List<TabInfo> myVisibleInfos = new ArrayList<TabInfo>();
  private final Map<TabInfo, Integer> myHiddenInfos = new HashMap<TabInfo, Integer>();

  private TabInfo mySelectedInfo;
  public final Map<TabInfo, TabLabel> myInfo2Label = new HashMap<TabInfo, TabLabel>();
  public final Map<TabInfo, Toolbar> myInfo2Toolbar = new HashMap<TabInfo, Toolbar>();
  public Dimension myHeaderFitSize;

  private Insets myInnerInsets = JBInsets.NONE;

  private final List<EventListener> myTabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<TabsListener> myTabListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myFocused;

  private Getter<ActionGroup> myPopupGroup;
  private String myPopupPlace;

  TabInfo myPopupInfo;
  final DefaultActionGroup myNavigationActions;

  final PopupMenuListener myPopupListener;
  JPopupMenu myActivePopup;

  public boolean myHorizontalSide = true;

  private boolean myStealthTabMode = false;

  private boolean mySideComponentOnTabs = true;

  private DataProvider myDataProvider;

  private final WeakHashMap<Component, Component> myDeferredToRemove = new WeakHashMap<Component, Component>();

  private final SingleRowLayout mySingleRowLayout;
  private final TableLayout myTableLayout = new TableLayout(this);


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
  final Set<TabInfo> myAttractions = new HashSet<TabInfo>();
  private final Animator myAnimator;
  private List<TabInfo> myAllTabs;
  private boolean myPaintBlocked;
  private BufferedImage myImage;
  private IdeFocusManager myFocusManager;
  private final boolean myAdjustBorders = true;

  boolean myAddNavigationGroup = true;

  private boolean myGhostsAlwaysVisible = false;
  private boolean myDisposed;
  private boolean myToDrawBorderIfTabsHidden = true;
  private Color myActiveTabFillIn;

  private boolean myTabLabelActionsAutoHide;

  private final TabActionsAutoHideListener myTabActionsAutoHideListener = new TabActionsAutoHideListener();
  private IdeGlassPane myGlassPane;
  @NonNls private static final String LAYOUT_DONE = "Layout.done";
  @NonNls public static final String STRETCHED_BY_WIDTH = "Layout.stretchedByWidth";

  private TimedDeadzone.Length myTabActionsMouseDeadzone = TimedDeadzone.DEFAULT;

  private long myRemoveDeferredRequest;
  private boolean myTestMode;

  private JBTabsPosition myPosition = JBTabsPosition.top;

  private final TabsBorder myBorder = new TabsBorder(this);
  private BaseNavigationAction myNextAction;
  private BaseNavigationAction myPrevAction;

  private boolean myTabDraggingEnabled;
  private DragHelper myDragHelper;
  private boolean myNavigationActionsEnabled = true;
  private boolean myUseBufferedPaint = true;

  private boolean myOwnSwitchProvider = true;
  private SwitchProvider mySwitchDelegate;
  protected TabInfo myDropInfo;
  private int myDropInfoIndex;
  protected boolean myShowDropLocation = true;

  private TabInfo myOldSelection;
  private SelectionChangeHandler mySelectionChangeHandler;

  private Runnable myDeferredFocusRequest;

  public JBTabsImpl(@NotNull Project project) {
    this(project, project);
  }

  private JBTabsImpl(@NotNull Project project, @NotNull Disposable parent) {
    this(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), parent);
  }

  public JBTabsImpl(@Nullable Project project, IdeFocusManager focusManager, @NotNull Disposable parent) {
    this(project, ActionManager.getInstance(), focusManager, parent);
  }

  public JBTabsImpl(@Nullable Project project, ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    myProject = project;
    myActionManager = actionManager;
    myFocusManager = focusManager != null ? focusManager : IdeFocusManager.getGlobalInstance();

    setOpaque(true);
    setPaintBorder(-1, -1, -1, -1);

    Disposer.register(parent, this);

    myNavigationActions = new DefaultActionGroup();

    if (myActionManager != null) {
      myNextAction = new SelectNextAction(this, myActionManager);
      myPrevAction = new SelectPreviousAction(this, myActionManager);

      myNavigationActions.add(myNextAction);
      myNavigationActions.add(myPrevAction);
    }

    setUiDecorator(null);

    mySingleRowLayout = createSingleRowLayout();
    myLayout = mySingleRowLayout;

    myPopupListener = new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      }

      public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        disposePopupListener();
      }

      public void popupMenuCanceled(final PopupMenuEvent e) {
        disposePopupListener();
      }
    };

    addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (mySingleRowLayout.myLastSingRowLayout != null &&
            mySingleRowLayout.myLastSingRowLayout.moreRect != null &&
            mySingleRowLayout.myLastSingRowLayout.moreRect.contains(e.getPoint())) {
          showMorePopup(e);
        }
      }
    });
    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (mySingleRowLayout.myLastSingRowLayout != null) {
          mySingleRowLayout.scroll(e.getUnitsToScroll() * mySingleRowLayout.getScrollUnitIncrement());
          revalidateAndRepaint(false);
        }
      }
    });

    myAnimator = new Animator("JBTabs Attractions", 2, 500, true) {
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        repaintAttractions();
      }
    };

    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
      public Component getDefaultComponent(final Container aContainer) {
        return getToFocus();
      }
    });

    add(mySingleRowLayout.myLeftGhost);
    add(mySingleRowLayout.myRightGhost);


    new LazyUiDisposable<JBTabsImpl>(parent, this, this) {
      protected void initialize(@NotNull Disposable parent, @NotNull JBTabsImpl child, @Nullable Project project) {
        if (project != null) {
          myProject = project;
        }

        Disposer.register(child, myAnimator);
        Disposer.register(child, new Disposable() {
          public void dispose() {
            removeTimerUpdate();
          }
        });

        if (!myTestMode) {
          final IdeGlassPane gp = IdeGlassPaneUtil.find(child);
          if (gp != null) {
            gp.addMouseMotionPreprocessor(myTabActionsAutoHideListener, child);
            myGlassPane = gp;
          }

          UIUtil.addAwtListener(new AWTEventListener() {
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
  }

  protected SingleRowLayout createSingleRowLayout() {
    return new SingleRowLayout(this);
  }


  public JBTabs setNavigationActionBinding(String prevActionId, String nextActionId) {
    if (myNextAction != null) {
      myNextAction.reconnect(nextActionId);
    }
    if (myPrevAction != null) {
      myPrevAction.reconnect(prevActionId);
    }

    return this;
  }

  public boolean isEditorTabs() {
    return false;
  }

  public JBTabs setNavigationActionsEnabled(boolean enabled) {
    myNavigationActionsEnabled = enabled;
    return this;
  }

  public final boolean isDisposed() {
    return myDisposed;
  }

  public JBTabs setAdditionalSwitchProviderWhenOriginal(SwitchProvider delegate) {
    mySwitchDelegate = delegate;
    return this;
  }

  public static Image getComponentImage(TabInfo info) {
    JComponent cmp = info.getComponent();

    BufferedImage img;
    if (cmp.isShowing()) {
      final int width = cmp.getWidth();
      final int height = cmp.getHeight();
      img = UIUtil.createImage(width > 0 ? width : 500, height > 0 ? height : 500, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      cmp.paint(g);
    }
    else {
      img = UIUtil.createImage(500, 500, BufferedImage.TYPE_INT_ARGB);
    }
    return img;
  }

  public void dispose() {
    myDisposed = true;
    mySelectedInfo = null;
    resetTabsCache();
    myAttractions.clear();
    myVisibleInfos.clear();
    myUiDecorator = null;
    myImage = null;
    myActivePopup = null;
    myInfo2Label.clear();
    myInfo2Toolbar.clear();
    myTabListeners.clear();
  }

  void resetTabsCache() {
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

  public void addNotify() {
    super.addNotify();
    addTimerUpdate();

    if (myDeferredFocusRequest != null) {
      final Runnable request = myDeferredFocusRequest;
      myDeferredFocusRequest = null;

      request.run();
    }
  }

  public void removeNotify() {
    super.removeNotify();

    setFocused(false);

    removeTimerUpdate();

    if (ScreenUtil.isStandardAddRemoveNotify(this) && myGlassPane != null) {
      myGlassPane.removeMouseMotionPreprocessor(myTabActionsAutoHideListener);
      myGlassPane = null;
    }
  }

  @Override
  public void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
  }

  private void addTimerUpdate() {
    if (myActionManager != null && !myListenerAdded) {
      myActionManager.addTimerListener(500, this);
      myListenerAdded = true;
    }
  }

  private void removeTimerUpdate() {
    if (myActionManager != null && myListenerAdded) {
      myActionManager.removeTimerListener(this);
      myListenerAdded = false;
    }
  }

  void setTestMode(final boolean testMode) {
    myTestMode = testMode;
  }

  public void layoutComp(SingleRowPassInfo data, int deltaX, int deltaY, int deltaWidth, int deltaHeight) {
    if (data.hToolbar != null) {
      final int toolbarHeight = data.hToolbar.getPreferredSize().height;
      final Rectangle compRect = layoutComp(deltaX, toolbarHeight + deltaY, data.comp, deltaWidth, deltaHeight);
      layout(data.hToolbar, compRect.x, compRect.y - toolbarHeight, compRect.width, toolbarHeight);
    }
    else if (data.vToolbar != null) {
      final int toolbarWidth = data.vToolbar.getPreferredSize().width;
      final Rectangle compRect = layoutComp(toolbarWidth + deltaX, deltaY, data.comp, deltaWidth, deltaHeight);
      layout(data.vToolbar, compRect.x - toolbarWidth, compRect.y, toolbarWidth, compRect.height);
    }
    else {
      layoutComp(deltaX, deltaY, data.comp, deltaWidth, deltaHeight);
    }
  }

  public boolean isDropTarget(TabInfo info) {
    return myDropInfo != null && myDropInfo == info;
  }

  protected void setDropInfoIndex(int dropInfoIndex) {
    myDropInfoIndex = dropInfoIndex;
  }

  class TabActionsAutoHideListener extends MouseMotionAdapter {

    private TabLabel myCurrentOverLabel;
    private Point myLastOverPoint;

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


  public ModalityState getModalityState() {
    return ModalityState.stateForComponent(this);
  }

  public void run() {
    updateTabActions(false);
  }

  public void updateTabActions(final boolean validateNow) {
    final Ref<Boolean> changed = new Ref<Boolean>(Boolean.FALSE);
    for (final TabInfo eachInfo : myInfo2Label.keySet()) {
      updateTab(new Computable<Boolean>() {
        public Boolean compute() {
          final boolean changes = myInfo2Label.get(eachInfo).updateTabActions();
          changed.set(changed.get().booleanValue() || changes);
          return changes;
        }
      }, eachInfo);
    }

    if (changed.get().booleanValue()) {
      if (validateNow) {
        validate();
        paintImmediately(0, 0, getWidth(), getHeight());
      }
    }
  }

  public boolean canShowMorePopup() {
    final SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
    return lastLayout != null && lastLayout.moreRect != null;
  }

  public void showMorePopup(@Nullable final MouseEvent e) {
    final SingleRowPassInfo lastLayout = mySingleRowLayout.myLastSingRowLayout;
    if (lastLayout == null) {
      return;
    }
    mySingleRowLayout.myMorePopup = new JBPopupMenu();
    float grayPercent = 0.9f;
    for (final TabInfo each : myVisibleInfos) {
      final JMenuItem item = new JBCheckboxMenuItem(each.getText(), getSelectedInfo() == each);
      item.setIcon(each.getIcon());
      Color color = UIManager.getColor("MenuItem.background");
      if (color != null) {
        if (mySingleRowLayout.isTabHidden(each)) {
          color = new Color((int) (color.getRed() * grayPercent), (int) (color.getGreen() * grayPercent), (int) (color.getBlue() * grayPercent));
        }
        item.setBackground(color);
      }

      mySingleRowLayout.myMorePopup.add(item);
      item.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          select(each, true);
        }
      });
    }

    mySingleRowLayout.myMorePopup.addPopupMenuListener(new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      }

      public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        mySingleRowLayout.myMorePopup = null;
      }

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

  public void requestFocus() {
    final JComponent toFocus = getToFocus();
    if (toFocus != null) {
      toFocus.requestFocus();
    }
    else {
      super.requestFocus();
    }
  }

  public boolean requestFocusInWindow() {
    final JComponent toFocus = getToFocus();
    if (toFocus != null) {
      return toFocus.requestFocusInWindow();
    }
    else {
      return super.requestFocusInWindow();
    }
  }


  @NotNull
  public TabInfo addTab(TabInfo info, int index) {
    return addTab(info, index, false, true);
  }

  public TabInfo addTabSilently(TabInfo info, int index) {
    return addTab(info, index, false, false);
  }

  private TabInfo addTab(TabInfo info, int index, boolean isDropTarget, boolean fireEvents) {
    if (!isDropTarget && getTabs().contains(info)) {
      return getTabs().get(getTabs().indexOf(info));
    }

    info.getChangeSupport().addPropertyChangeListener(this);
    final TabLabel label = createTabLabel(info);
    myInfo2Label.put(info, label);

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

  protected TabLabel createTabLabel(TabInfo info) {
    return new TabLabel(this, info);
  }

  @NotNull
  public TabInfo addTab(TabInfo info) {
    return addTab(info, -1);
  }

  @Nullable
  public ActionGroup getPopupGroup() {
    return myPopupGroup != null ? myPopupGroup.get() : null;
  }

  public String getPopupPlace() {
    return myPopupPlace;
  }

  public JBTabs setPopupGroup(@NotNull final ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup) {
    return setPopupGroup(new Getter<ActionGroup>() {
      public ActionGroup get() {
        return popupGroup;
      }
    }, place, addNavigationGroup);
  }

  public JBTabs setPopupGroup(@NotNull final Getter<ActionGroup> popupGroup,
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

  public ActionCallback select(@NotNull TabInfo info, boolean requestFocus) {
    return _setSelected(info, requestFocus);
  }

  private ActionCallback _setSelected(final TabInfo info, final boolean requestFocus) {
    if (mySelectionChangeHandler != null) {
      return mySelectionChangeHandler.execute(info, requestFocus, new ActiveRunnable() {
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

  private ActionCallback executeSelectionChange(TabInfo info, boolean requestFocus) {
    if (mySelectedInfo != null && mySelectedInfo.equals(info)) {
      if (!requestFocus) {
        return new ActionCallback.Done();
      }
      else {
        Component owner = myFocusManager.getFocusOwner();
        JComponent c = info.getComponent();
        if (c != null && owner != null) {
          if (c == owner || SwingUtilities.isDescendingFrom(owner, c)) {
            return new ActionCallback.Done();
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

    fireBeforeSelectionChanged(oldInfo, newInfo);

    updateContainer(false, true);

    fireSelectionChanged(oldInfo, newInfo);

    if (requestFocus) {
      final JComponent toFocus = getToFocus();
      if (myProject != null && toFocus != null) {
        final ActionCallback result = new ActionCallback();
        requestFocus(toFocus).doWhenProcessed(new Runnable() {
          public void run() {
            if (myDisposed) {
              result.setRejected();
            }
            else {
              removeDeferred().notifyWhenDone(result);
            }
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

  private ActionCallback requestFocus(final JComponent toFocus) {
    if (toFocus == null) return new ActionCallback.Done();

    if (myTestMode) {
      toFocus.requestFocus();
      return new ActionCallback.Done();
    }


    if (isShowing()) {
      return myFocusManager.requestFocus(new FocusCommand.ByComponent(toFocus), true);
    }
    else {
      final ActionCallback result = new ActionCallback();
      final FocusRequestor requestor = myFocusManager.getFurtherRequestor();
      final Ref<Boolean> queued = new Ref<Boolean>(false);
      Disposer.register(requestor, new Disposable() {
        @Override
        public void dispose() {
          if (!queued.get()) {
            result.setRejected();
          }
        }
      });
      myDeferredFocusRequest = new Runnable() {
        @Override
        public void run() {
          queued.set(true);
          requestor.requestFocus(new FocusCommand.ByComponent(toFocus), true).notify(result);
        }
      };
      return result;
    }
  }

  private ActionCallback removeDeferred() {
    final ActionCallback callback = new ActionCallback();

    final long executionRequest = ++myRemoveDeferredRequest;

    final Runnable onDone = new Runnable() {
      public void run() {
        if (myRemoveDeferredRequest == executionRequest) {
          removeDeferredNow();
        }

        callback.setDone();
      }
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
    updateTab(new Computable<Boolean>() {
      public Boolean compute() {
        myInfo2Label.get(tabInfo).setIcon(tabInfo.getIcon());
        return true;
      }
    }, tabInfo);
  }

  private void updateColor(final TabInfo tabInfo) {
    myInfo2Label.get(tabInfo).setInactiveStateImage(null);

    updateTab(new Computable<Boolean>() {
      public Boolean compute() {
        repaint();
        return true;
      }
    }, tabInfo);
  }

  private void updateTab(Computable<Boolean> update, TabInfo info) {
    final TabLabel label = myInfo2Label.get(info);
    Boolean changes = update.compute();
    if (label.getRootPane() != null) {
      if (label.isValid()) {
        if (changes) {
          label.repaint();
        }
      }
      else {
        revalidateAndRepaint(false);
      }
    }
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
    updateTab(new Computable<Boolean>() {
      public Boolean compute() {
        final TabLabel label = myInfo2Label.get(tabInfo);
        label.setText(tabInfo.getColoredText());
        label.setToolTipText(tabInfo.getTooltipText());
        return true;
      }
    }, tabInfo);
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

  @Nullable
  public TabInfo getSelectedInfo() {
    if (myOldSelection != null) return myOldSelection;

    if (!myVisibleInfos.contains(mySelectedInfo)) {
      mySelectedInfo = null;
    }
    return mySelectedInfo != null ? mySelectedInfo : !myVisibleInfos.isEmpty() ? myVisibleInfos.get(0) : null;
  }

  @Nullable
  private TabInfo getToSelectOnRemoveOf(TabInfo info) {
    if (!myVisibleInfos.contains(info)) return null;
    if (mySelectedInfo != info) return null;

    if (myVisibleInfos.size() == 1) return null;

    int index = myVisibleInfos.indexOf(info);

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
  private TabInfo findEnabledForward(int from, boolean cycle) {
    if (from < 0) return null;
    int index = from;
    while (true) {
      index++;
      if (index == myVisibleInfos.size()) {
        if (!cycle) break;
        index = 0;
      }
      if (index == from) break;
      final TabInfo each = myVisibleInfos.get(index);
      if (each.isEnabled()) return each;
    }

    return null;
  }

  @Nullable
  private TabInfo findEnabledBackward(int from, boolean cycle) {
    if (from < 0) return null;
    int index = from;
    while (true) {
      index--;
      if (index == -1) {
        if (!cycle) break;
        index = myVisibleInfos.size() - 1;
      }
      if (index == from) break;
      final TabInfo each = myVisibleInfos.get(index);
      if (each.isEnabled()) return each;
    }

    return null;
  }

  protected Toolbar createToolbarComponent(final TabInfo tabInfo) {
    return new Toolbar(this, tabInfo);
  }

  @NotNull
  public TabInfo getTabAt(final int tabIndex) {
    return getTabs().get(tabIndex);
  }

  @NotNull
  public List<TabInfo> getTabs() {
    if (myAllTabs != null) return myAllTabs;

    ArrayList<TabInfo> result = new ArrayList<TabInfo>();
    result.addAll(myVisibleInfos);

    for (TabInfo each : myHiddenInfos.keySet()) {
      result.add(getIndexInVisibleArray(each), each);
    }

    myAllTabs = result;

    return result;
  }

  public TabInfo getTargetInfo() {
    return myPopupInfo != null ? myPopupInfo : getSelectedInfo();
  }

  public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
  }

  public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
    resetPopup();
  }

  public void popupMenuCanceled(final PopupMenuEvent e) {
    resetPopup();
  }

  private void resetPopup() {
//todo [kirillk] dirty hack, should rely on ActionManager to understand that menu item was either chosen on or cancelled
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPopupInfo = null;
      }
    });
  }

  public void setPaintBlocked(boolean blocked, final boolean takeSnapshot) {
    if (blocked && !myPaintBlocked) {
      if (takeSnapshot) {
        if (getWidth() > 0 && getHeight() > 0) {
          myImage = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
          final Graphics2D g = myImage.createGraphics();
          super.paint(g);
          g.dispose();
        }
      }
    }

    myPaintBlocked = blocked;

    if (!myPaintBlocked) {
      if (myImage != null) {
        myImage.flush();
      }

      myImage = null;
      repaint();
    }
  }


  private void addToDeferredRemove(final Component c) {
    if (!myDeferredToRemove.containsKey(c)) {
      myDeferredToRemove.put(c, c);
    }
  }

  private boolean isToDrawBorderIfTabsHidden() {
    return myToDrawBorderIfTabsHidden;
  }

  @NotNull
  public JBTabsPresentation setToDrawBorderIfTabsHidden(final boolean toDrawBorderIfTabsHidden) {
    myToDrawBorderIfTabsHidden = toDrawBorderIfTabsHidden;
    return this;
  }

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

      if (group != null && myTabs.myActionManager != null) {
        final String place = info.getPlace();
        ActionToolbar toolbar =
          myTabs.myActionManager.createActionToolbar(place != null ? place : ActionPlaces.UNKNOWN, group, myTabs.myHorizontalSide);
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


  public void doLayout() {
    try {
      myHeaderFitSize = computeHeaderFitSize();

      final Collection<TabLabel> labels = myInfo2Label.values();
      for (TabLabel each : labels) {
        each.setTabActionsAutoHide(myTabLabelActionsAutoHide);
      }


      List<TabInfo> visible = new ArrayList<TabInfo>();
      visible.addAll(myVisibleInfos);

      if (myDropInfo != null && !visible.contains(myDropInfo) && myShowDropLocation) {
        if (getDropInfoIndex() >= 0 && getDropInfoIndex() < visible.size()) {
          visible.add(getDropInfoIndex(), myDropInfo);
        }
        else {
          visible.add(myDropInfo);
        }
      }

      if (isSingleRow()) {
        myLastLayoutPass = mySingleRowLayout.layoutSingleRow(visible);
        myTableLayout.myLastTableLayout = null;
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
    final Insets insets = getLayoutInsets();

    final Insets border = isHideTabs() ? new Insets(0, 0, 0, 0) : myBorder.getEffectiveBorder();
    final boolean noTabsVisible = isStealthModeEffective() || isHideTabs();

    if (noTabsVisible) {
      border.top = getBorder(-1);
      border.bottom = getBorder(-1);
      border.left = getBorder(-1);
      border.right = getBorder(-1);
    }

    final Insets inner = getInnerInsets();
    border.top += inner.top;
    border.bottom += inner.bottom;
    border.left += inner.left;
    border.right += inner.right;


    int x = insets.left + componentX + border.left;
    int y = insets.top + componentY + border.top;
    int width = getWidth() - insets.left - insets.right - componentX - border.left - border.right;
    int height = getHeight() - insets.top - insets.bottom - componentY - border.top - border.bottom;

    if (!noTabsVisible) {
      width += deltaWidth;
      height += deltaHeight;
    }

    return layout(comp, x, y, width, height);
  }


  public JBTabsPresentation setInnerInsets(final Insets innerInsets) {
    myInnerInsets = innerInsets;
    return this;
  }

  private Insets getInnerInsets() {
    return myInnerInsets;
  }

  public Insets getLayoutInsets() {
    Insets insets = getInsets();
    if (insets == null) {
      insets = new Insets(0, 0, 0, 0);
    }
    return insets;
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

  private static int getEdgeArcSize() {
    return 3;
  }

  public static int getGhostTabLength() {
    return 15;
  }

  protected JBTabsPosition getPosition() {
    return myPosition;
  }

  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
    g2d.setColor(getBackground());
    g2d.fill(clip);
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myVisibleInfos.isEmpty()) return;

    Graphics2D g2d = (Graphics2D)g;

    final GraphicsConfig config = new GraphicsConfig(g2d);
    config.setAntialiasing(true);

    final Rectangle clip = g2d.getClipBounds();

    doPaintBackground(g2d, clip);

    final TabInfo selected = getSelectedInfo();

    if (selected != null) {
      Rectangle compBounds = selected.getComponent().getBounds();
      if (compBounds.contains(clip) && !compBounds.intersects(clip)) return;
    }

    boolean leftGhostExists = isSingleRow();
    boolean rightGhostExists = isSingleRow();

    if (!isStealthModeEffective() && !isHideTabs()) {
      if (isSingleRow() && mySingleRowLayout.myLastSingRowLayout.lastGhostVisible) {
        paintLastGhost(g2d);
      }


      paintNonSelectedTabs(g2d, leftGhostExists, rightGhostExists);

      if (isSingleRow() && mySingleRowLayout.myLastSingRowLayout.firstGhostVisible) {
        paintFirstGhost(g2d);
      }
    }

    config.setAntialiasing(false);

    if (isSideComponentVertical()) {
      Toolbar toolbarComp = myInfo2Toolbar.get(mySelectedInfo);
      if (toolbarComp != null && !toolbarComp.isEmpty()) {
        Rectangle toolBounds = toolbarComp.getBounds();
        g2d.setColor(CaptionPanel.CNT_ACTIVE_BORDER_COLOR);
        g2d.drawLine((int)toolBounds.getMaxX(), toolBounds.y, (int)toolBounds.getMaxX(), (int)toolBounds.getMaxY() - 1);
      }
    }
    else if (!isSideComponentOnTabs()) {
      Toolbar toolbarComp = myInfo2Toolbar.get(mySelectedInfo);
      if (toolbarComp != null && !toolbarComp.isEmpty()) {
        Rectangle toolBounds = toolbarComp.getBounds();
        g2d.setColor(CaptionPanel.CNT_ACTIVE_BORDER_COLOR);
        g2d.drawLine(toolBounds.x, (int)toolBounds.getMaxY(), (int)toolBounds.getMaxX() - 1, (int)toolBounds.getMaxY());
      }
    }

    config.restore();
  }

  @Nullable
  protected Color getActiveTabColor(@Nullable final Color c) {
    final TabInfo info = getSelectedInfo();
    if (info == null) {
      return c;
    }

    final Color tabColor = info.getTabColor();
    return tabColor == null ? c : tabColor;
  }

  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (mySelectedInfo == null) return;

    final ShapeInfo shapeInfo = computeSelectedLabelShape();
    if (!isHideTabs()) {
      g2d.setColor(getBackground());
      g2d.fill(shapeInfo.fillPath.getShape());
    }

    final int alpha;
    int paintTopY = shapeInfo.labelTopY;
    int paintBottomY = shapeInfo.labelBottomY;
    final boolean paintFocused = myPaintFocus && (myFocused || myActivePopup != null);
    Color bgPreFill = null;
    if (paintFocused) {
      final Color bgColor = getActiveTabColor(getActiveTabFillIn());
      if (bgColor == null) {
        shapeInfo.from = getFocusedTopFillColor();
        shapeInfo.to = getFocusedBottomFillColor();
      }
      else {
        bgPreFill = bgColor;
        alpha = 255;
        paintBottomY = shapeInfo.labelTopY + shapeInfo.labelPath.deltaY(getArcSize() - 2);
        shapeInfo.from = ColorUtil.toAlpha(UIUtil.getFocusedFillColor(), alpha);
        shapeInfo.to = ColorUtil.toAlpha(getActiveTabFillIn(), alpha);
      }
    }
    else {
      final Color bgColor = getActiveTabColor(getActiveTabFillIn());
      if (isPaintFocus()) {
        if (bgColor == null) {
          alpha = 150;
          shapeInfo.from = ColorUtil.toAlpha(UIUtil.getPanelBackground().brighter(), alpha);
          shapeInfo.to = ColorUtil.toAlpha(UIUtil.getPanelBackground(), alpha);
        }
        else {
          alpha = 255;
          shapeInfo.from = ColorUtil.toAlpha(bgColor, alpha);
          shapeInfo.to = ColorUtil.toAlpha(bgColor, alpha);
        }
      }
      else {
        alpha = 255;
        final Color tabColor = getActiveTabColor(null);
        final Color defaultBg = UIUtil.isUnderDarcula() ? UIUtil.getControlColor() : Color.white;
        shapeInfo.from = tabColor == null ? defaultBg : tabColor;
        shapeInfo.to = tabColor == null ? defaultBg : tabColor;
      }
    }

    if (!isHideTabs()) {
      if (bgPreFill != null) {
        g2d.setColor(bgPreFill);
        g2d.fill(shapeInfo.fillPath.getShape());
      }

      final Line2D.Float gradientLine =
        shapeInfo.fillPath.transformLine(shapeInfo.fillPath.getX(), paintTopY, shapeInfo.fillPath.getX(), paintBottomY);


      g2d.setPaint(UIUtil.getGradientPaint((float)gradientLine.getX1(), (float)gradientLine.getY1(),
                                     shapeInfo.fillPath.transformY1(shapeInfo.from, shapeInfo.to), (float)gradientLine.getX2(),
                                     (float)gradientLine.getY2(), shapeInfo.fillPath.transformY1(shapeInfo.to, shapeInfo.from)));
      g2d.fill(shapeInfo.fillPath.getShape());
    }

    final Color tabColor = getActiveTabColor(null);
    Color borderColor = tabColor == null ? UIUtil.getBoundsColor(paintFocused) : tabColor.darker();
    g2d.setColor(borderColor);

    if (!isHideTabs()) {
      g2d.draw(shapeInfo.path.getShape());
    }

    paintBorder(g2d, shapeInfo, borderColor);
  }

  protected Color getFocusedTopFillColor() {
    return UIUtil.getFocusedFillColor();
  }

  protected Color getFocusedBottomFillColor() {
    return UIUtil.getFocusedFillColor();
  }

  protected ShapeInfo computeSelectedLabelShape() {
    final ShapeInfo shape = new ShapeInfo();

    shape.path = getEffectiveLayout().createShapeTransform(getSize());
    shape.insets = shape.path.transformInsets(getLayoutInsets());
    shape.labelPath = shape.path.createTransform(getSelectedLabel().getBounds());

    shape.labelBottomY = shape.labelPath.getMaxY() + shape.labelPath.deltaY(1);
    shape.labelTopY = shape.labelPath.getY();
    shape.labelLeftX = shape.labelPath.getX();
    shape.labelRightX = shape.labelPath.getX() + shape.labelPath.deltaX(shape.labelPath.getWidth());

    Insets border = myBorder.getEffectiveBorder();
    TabInfo selected = getSelectedInfo();
    boolean first = myLastLayoutPass.getPreviousFor(selected) == null;
    boolean last = myLastLayoutPass.getNextFor(selected) == null;

    boolean leftEdge = !isSingleRow() && first && border.left == 0;
    boolean rightEdge =
      !isSingleRow() && last && Boolean.TRUE.equals(myInfo2Label.get(selected).getClientProperty(STRETCHED_BY_WIDTH)) && border.right == 0;

    boolean isDraggedNow = selected != null && myDragHelper != null && selected.equals(myDragHelper.getDragSource());

    if (leftEdge && !isDraggedNow) {
      shape.path.moveTo(shape.insets.left, shape.labelTopY + shape.labelPath.deltaY(getEdgeArcSize()));
      shape.path.quadTo(shape.labelLeftX, shape.labelTopY, shape.labelLeftX + shape.labelPath.deltaX(getEdgeArcSize()), shape.labelTopY);
      shape.path.lineTo(shape.labelRightX - shape.labelPath.deltaX(getArcSize()), shape.labelTopY);
    }
    else {
      shape.path.moveTo(shape.insets.left, shape.labelBottomY);
      shape.path.lineTo(shape.labelLeftX, shape.labelBottomY);
      shape.path.lineTo(shape.labelLeftX, shape.labelTopY + shape.labelPath.deltaY(getArcSize()));
      shape.path.quadTo(shape.labelLeftX, shape.labelTopY, shape.labelLeftX + shape.labelPath.deltaX(getArcSize()), shape.labelTopY);
    }

    int lastX = shape.path.getWidth() - shape.path.deltaX(shape.insets.right + 1);

    if (isStealthModeEffective()) {
      shape.path.lineTo(lastX - shape.path.deltaX(getArcSize()), shape.labelTopY);
      shape.path.quadTo(lastX, shape.labelTopY, lastX, shape.labelTopY + shape.path.deltaY(getArcSize()));
      shape.path.lineTo(lastX, shape.labelBottomY);
    }
    else {
      if (rightEdge) {
        shape.path.lineTo(shape.labelRightX + 1 - shape.path.deltaX(getArcSize()), shape.labelTopY);
        shape.path.quadTo(shape.labelRightX + 1, shape.labelTopY, shape.labelRightX + 1, shape.labelTopY + shape.path.deltaY(getArcSize()));
      }
      else {
        shape.path.lineTo(shape.labelRightX - shape.path.deltaX(getArcSize()), shape.labelTopY);
        shape.path.quadTo(shape.labelRightX, shape.labelTopY, shape.labelRightX, shape.labelTopY + shape.path.deltaY(getArcSize()));
      }
      if (myLastLayoutPass.hasCurveSpaceFor(selected)) {
        shape.path.lineTo(shape.labelRightX, shape.labelBottomY - shape.path.deltaY(getArcSize()));
        shape.path.quadTo(shape.labelRightX, shape.labelBottomY, shape.labelRightX + shape.path.deltaX(getArcSize()), shape.labelBottomY);
      }
      else {
        if (rightEdge) {
          shape.path.lineTo(shape.labelRightX + 1, shape.labelBottomY);
        }
        else {
          shape.path.lineTo(shape.labelRightX, shape.labelBottomY);
        }
      }
    }

    if (!rightEdge) {
      shape.path.lineTo(lastX, shape.labelBottomY);
    }

    if (isStealthModeEffective()) {
      shape.path.closePath();
    }

    shape.fillPath = shape.path.copy();
    if (!isHideTabs()) {
      shape.fillPath.lineTo(lastX, shape.labelBottomY + shape.fillPath.deltaY(1));
      shape.fillPath.lineTo(shape.labelLeftX, shape.labelBottomY + shape.fillPath.deltaY(1));
      shape.fillPath.closePath();
    }
    return shape;
  }

  protected TabLabel getSelectedLabel() {
    return myInfo2Label.get(getSelectedInfo());
  }

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


  protected void paintFirstGhost(Graphics2D g2d) {
    final ShapeTransform path = getEffectiveLayout().createShapeTransform(mySingleRowLayout.myLastSingRowLayout.firstGhost);

    int topX = path.getX() + path.deltaX(getCurveArc());
    int topY = path.getY() + path.deltaY(getSelectionTabVShift());
    int bottomX = path.getMaxX() + path.deltaX(1);
    int bottomY = path.getMaxY() + path.deltaY(1);

    path.moveTo(topX, topY);

    final boolean isLeftFromSelection = mySingleRowLayout.myLastSingRowLayout.toLayout.indexOf(getSelectedInfo()) == 0;

    if (isLeftFromSelection) {
      path.lineTo(bottomX, topY);
    }
    else {
      path.lineTo(bottomX - getArcSize(), topY);
      path.quadTo(bottomX, topY, bottomX, topY + path.deltaY(getArcSize()));
    }

    path.lineTo(bottomX, bottomY);
    path.lineTo(topX, bottomY);

    path.quadTo(topX - path.deltaX(getCurveArc() * 2 - 1), bottomY - path.deltaY(Math.abs(bottomY - topY) / 4), topX,
                bottomY - path.deltaY(Math.abs(bottomY - topY) / 2));

    path.quadTo(topX + path.deltaX(getCurveArc() - 1), topY + path.deltaY(Math.abs(bottomY - topY) / 4), topX, topY);

    path.closePath();

    g2d.setColor(getBackground());
    g2d.fill(path.getShape());

    g2d.setColor(getBoundsColor());
    g2d.draw(path.getShape());

    g2d.setColor(getTopBlockColor());
    g2d.drawLine(topX + path.deltaX(1), topY + path.deltaY(1), bottomX - path.deltaX(getArcSize()), topY + path.deltaY(1));

    g2d.setColor(getRightBlockColor());
    g2d.drawLine(bottomX - path.deltaX(1), topY + path.deltaY(getArcSize()), bottomX - path.deltaX(1), bottomY - path.deltaY(1));
  }

  protected void paintLastGhost(Graphics2D g2d) {
    final ShapeTransform path = getEffectiveLayout().createShapeTransform(mySingleRowLayout.myLastSingRowLayout.lastGhost);

    int topX = path.getX() - path.deltaX(getArcSize());
    int topY = path.getY() + path.deltaY(getSelectionTabVShift());
    int bottomX = path.getMaxX() - path.deltaX(getCurveArc());
    int bottomY = path.getMaxY() + path.deltaY(1);

    path.moveTo(topX, topY);
    path.lineTo(bottomX, topY);
    path.quadTo(bottomX - getCurveArc(), topY + (bottomY - topY) / 4, bottomX, topY + (bottomY - topY) / 2);
    path.quadTo(bottomX + getCurveArc(), bottomY - (bottomY - topY) / 4, bottomX, bottomY);
    path.lineTo(topX, bottomY);

    path.closePath();

    g2d.setColor(getBackground());
    g2d.fill(path.getShape());

    g2d.setColor(getBoundsColor());
    g2d.draw(path.getShape());

    g2d.setColor(getTopBlockColor());
    g2d.drawLine(topX, topY + path.deltaY(1), bottomX - path.deltaX(getCurveArc()), topY + path.deltaY(1));
  }

  private static int getCurveArc() {
    return 2;
  }

  private static Color getBoundsColor() {
    return new JBColor(Color.gray, Gray._0.withAlpha(80));
  }

  private static Color getRightBlockColor() {
    return new JBColor(Color.lightGray, Gray._0.withAlpha(0));
  }

  private static Color getTopBlockColor() {
    return new JBColor(Color.white, Gray._0.withAlpha(0));
  }

  private void paintNonSelectedTabs(final Graphics2D g2d, final boolean leftGhostExists, final boolean rightGhostExists) {
    TabInfo selected = getSelectedInfo();
    if (myLastPaintedSelection == null || !myLastPaintedSelection.equals(selected)) {
      List<TabInfo> tabs = getTabs();
      for (TabInfo each : tabs) {
        myInfo2Label.get(each).setInactiveStateImage(null);
      }
    }

    for (int eachRow = 0; eachRow < myLastLayoutPass.getRowCount(); eachRow++) {
      for (int eachColumn = myLastLayoutPass.getColumnCount(eachRow) - 1; eachColumn >= 0; eachColumn--) {
        final TabInfo each = myLastLayoutPass.getTabAt(eachRow, eachColumn);
        if (getSelectedInfo() == each) {
          continue;
        }
        paintNonSelected(g2d, each, leftGhostExists, rightGhostExists, eachRow, eachColumn);
      }
    }

    myLastPaintedSelection = selected;
  }

  private void paintNonSelected(final Graphics2D g2d,
                                final TabInfo each,
                                final boolean leftGhostExists,
                                final boolean rightGhostExists,
                                int row, int column) {
    if (myDropInfo == each) return;

    final TabLabel label = myInfo2Label.get(each);
    if (label.getBounds().width == 0) return;

    int imageInsets = getArcSize() + 1;

    Rectangle bounds = label.getBounds();

    int x = bounds.x - imageInsets;
    int y = bounds.y;
    int width = bounds.width + imageInsets * 2 + 1;
    int height = bounds.height + getArcSize() + 1;

    if (isToBufferPainting()) {
      BufferedImage img = label.getInactiveStateImage(bounds);

      if (img == null) {
        img = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D imgG2d = img.createGraphics();
        imgG2d.addRenderingHints(g2d.getRenderingHints());
        doPaintInactive(imgG2d, leftGhostExists, label, new Rectangle(imageInsets, 0, label.getWidth(), label.getHeight()),
                        rightGhostExists, row, column);
        imgG2d.dispose();
      }

      g2d.drawImage(img, x, y, width, height, null);

      label.setInactiveStateImage(img);
    }
    else {
      doPaintInactive(g2d, leftGhostExists, label, label.getBounds(), rightGhostExists, row, column);
      label.setInactiveStateImage(null);
    }
  }

  private boolean isToBufferPainting() {
    return Registry.is("ide.tabbedPane.bufferedPaint") && myUseBufferedPaint;
  }

  protected List<TabInfo> getVisibleInfos() {
    return myVisibleInfos;
  }

  protected LayoutPassInfo getLastLayoutPass() {
    return myLastLayoutPass;
  }

  @Override
  public Color getBackground() {
    return UIUtil.isUnderNimbusLookAndFeel() ? UIUtil.getPanelBackground() : super.getBackground();
  }

  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists, int row, int column) {
    int tabIndex = myVisibleInfos.indexOf(label.getInfo());

    final int arc = getArcSize();
    Color topBlockColor = getTopBlockColor();
    Color rightBlockColor = getRightBlockColor();
    Color boundsColor = getBoundsColor();
    Color backgroundColor = getBackground();

    final Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      backgroundColor = tabColor;
      boundsColor = tabColor.darker();
      topBlockColor = tabColor.brighter().brighter();
      rightBlockColor = tabColor;
    }

    final TabInfo selected = getSelectedInfo();
    final int selectionTabVShift = getSelectionTabVShift();


    final TabInfo prev = myLastLayoutPass.getPreviousFor(myVisibleInfos.get(tabIndex));
    final TabInfo next = myLastLayoutPass.getNextFor(myVisibleInfos.get(tabIndex));


    boolean firstShowing = prev == null;
    if (!firstShowing && !leftGhostExists) {
      firstShowing = myInfo2Label.get(prev).getBounds().width == 0;
    }

    boolean lastShowing = next == null;
    if (!lastShowing) {
      lastShowing = myInfo2Label.get(next).getBounds().width == 0;
    }

    boolean leftFromSelection = selected != null && tabIndex == myVisibleInfos.indexOf(selected) - 1;

    final ShapeTransform shape = getEffectiveLayout().createShapeTransform(effectiveBounds);

    int leftX = firstShowing ? shape.getX() : shape.getX() - shape.deltaX(arc + 1);
    int topY = shape.getY() + shape.deltaY(selectionTabVShift);
    int rightX = !lastShowing && leftFromSelection ? shape.getMaxX() + shape.deltaX(arc + 1) : shape.getMaxX();
    int bottomY = shape.getMaxY() + shape.deltaY(1);

    Insets border = myBorder.getEffectiveBorder();

    if (border.left > 0 || leftGhostExists || !firstShowing) {
      shape.moveTo(leftX, bottomY);
      shape.lineTo(leftX, topY + shape.deltaY(arc));
      shape.quadTo(leftX, topY, leftX + shape.deltaX(arc), topY);
    }
    else {
      if (firstShowing) {
        shape.moveTo(leftX, topY + shape.deltaY(getEdgeArcSize()));
        shape.quadTo(leftX, topY, leftX + shape.deltaX(getEdgeArcSize()), topY);
      }
    }

    boolean rightEdge = false;
    if (border.right > 0 || rightGhostExists || !lastShowing || !Boolean.TRUE.equals(label.getClientProperty(STRETCHED_BY_WIDTH))) {
      shape.lineTo(rightX - shape.deltaX(arc), topY);
      shape.quadTo(rightX, topY, rightX, topY + shape.deltaY(arc));
      shape.lineTo(rightX, bottomY);
    }
    else {
      if (lastShowing) {
        shape.lineTo(rightX - shape.deltaX(arc), topY);
        shape.quadTo(rightX + 1, topY, rightX + 1, topY + shape.deltaY(arc));

        shape.lineTo(rightX + 1, bottomY);
        rightEdge = true;
      }
    }

    if (!isSingleRow()) {
      final TablePassInfo info = myTableLayout.myLastTableLayout;
      if (!info.isInSelectionRow(label.getInfo())) {
        shape.lineTo(rightX, bottomY + shape.deltaY(getArcSize()));
        shape.lineTo(leftX, bottomY + shape.deltaY(getArcSize()));
        shape.lineTo(leftX, bottomY);
      }
    }

    if (!rightEdge) {
      shape.lineTo(leftX, bottomY);
    }

    g2d.setColor(backgroundColor);
    g2d.fill(shape.getShape());

    // TODO

    final Line2D.Float gradientLine =
      shape.transformLine(0, topY, 0, topY + shape.deltaY((int)(shape.getHeight() / 1.5)));

    final Paint gp = UIUtil.isUnderDarcula()
                             ? UIUtil.getGradientPaint(gradientLine.x1, gradientLine.y1,
                                                 shape.transformY1(backgroundColor, backgroundColor),
                                                 gradientLine.x2, gradientLine.y2,
                                                 shape.transformY1(backgroundColor, backgroundColor))
                             : UIUtil.getGradientPaint(gradientLine.x1, gradientLine.y1,
                                                 shape.transformY1(backgroundColor.brighter().brighter(), backgroundColor),
                                                 gradientLine.x2, gradientLine.y2,
                                                 shape.transformY1(backgroundColor, backgroundColor.brighter().brighter()));

    final Paint old = g2d.getPaint();
    g2d.setPaint(gp);
    g2d.fill(shape.getShape());
    g2d.setPaint(old);

    g2d.setColor(topBlockColor);
    g2d.draw(
      shape.transformLine(leftX + shape.deltaX(arc + 1), topY + shape.deltaY(1), rightX - shape.deltaX(arc - 1), topY + shape.deltaY(1)));

    if (!rightEdge) {
      g2d.setColor(rightBlockColor);
      g2d.draw(shape.transformLine(rightX - shape.deltaX(1), topY + shape.deltaY(arc - 1), rightX - shape.deltaX(1), bottomY));
    }

    g2d.setColor(boundsColor);
    g2d.draw(shape.getShape());
  }

  public static int getSelectionTabVShift() {
    return 2;
  }

  protected void paintBorder(Graphics2D g2d, ShapeInfo shape, final Color borderColor) {
    final ShapeTransform shaper = shape.path.copy().reset();

    final Insets paintBorder = shape.path.transformInsets(myBorder.getEffectiveBorder());

    int topY = shape.labelPath.getMaxY() + shape.labelPath.deltaY(1);

    int bottomY = topY + paintBorder.top - 2;
    int middleY = topY + (bottomY - topY) / 2;


    final int boundsX = shape.path.getX() + shape.path.deltaX(shape.insets.left);

    final int boundsY =
      isHideTabs() ? shape.path.getY() + shape.path.deltaY(shape.insets.top) : shape.labelPath.getMaxY() + shape.path.deltaY(1);

    final int boundsHeight = Math.abs(shape.path.getMaxY() - boundsY) - shape.insets.bottom - paintBorder.bottom;
    final int boundsWidth = Math.abs(shape.path.getMaxX() - (shape.insets.left + shape.insets.right));

    if (paintBorder.top > 0) {
      if (isHideTabs()) {
        if (isToDrawBorderIfTabsHidden()) {
          g2d.setColor(borderColor);
          g2d.fill(shaper.reset().doRect(boundsX, boundsY, boundsWidth, 1).getShape());
        }
      }
      else {
        Color tabFillColor = getActiveTabColor(null);
        if (tabFillColor == null) {
          tabFillColor = shape.path.transformY1(shape.to, shape.from);
        }

        g2d.setColor(tabFillColor);
        g2d.fill(shaper.reset().doRect(boundsX, topY + shape.path.deltaY(1), boundsWidth, paintBorder.top - 1).getShape());

        g2d.setColor(borderColor);
        if (paintBorder.top == 2) {
          final Line2D.Float line = shape.path.transformLine(boundsX, topY, boundsX + shape.path.deltaX(boundsWidth - 1), topY);

          g2d.drawLine((int)line.x1, (int)line.y1, (int)line.x2, (int)line.y2);
        }
        else if (paintBorder.top > 2) {
//todo kirillk
//start hack
          int deltaY = 0;
          if (myPosition == JBTabsPosition.bottom || myPosition == JBTabsPosition.right) {
            deltaY = 1;
          }
//end hack
          final int topLine = topY + shape.path.deltaY(paintBorder.top - 1);
          g2d.fill(shaper.reset().doRect(boundsX, topLine + deltaY, boundsWidth - 1, 1).getShape());
        }
      }
    }

    g2d.setColor(borderColor);

    //bottom
    g2d.fill(shaper.reset().doRect(boundsX, Math.abs(shape.path.getMaxY() - shape.insets.bottom - paintBorder.bottom), boundsWidth,
                                   paintBorder.bottom).getShape());

    //left
    g2d.fill(shaper.reset().doRect(boundsX, boundsY, paintBorder.left, boundsHeight).getShape());

    //right
    g2d.fill(shaper.reset()
               .doRect(shape.path.getMaxX() - shape.insets.right - paintBorder.right, boundsY, paintBorder.right, boundsHeight).getShape());
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


  public void paint(final Graphics g) {
    Rectangle clip = g.getClipBounds();
    if (clip == null) {
      return;
    }

    if (myPaintBlocked) {
      if (myImage != null) {
        g.drawImage(myImage, 0, 0, getWidth(), getHeight(), null);
      }
      return;
    }

    super.paint(g);
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    final GraphicsConfig config = new GraphicsConfig(g);
    config.setAntialiasing(true);
    paintSelectionAndBorder((Graphics2D)g);
    config.restore();

    final TabLabel selected = getSelectedLabel();
    if (selected != null) {
      selected.paintImage(g);
    }

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

    max.myToolbar.height++;

    return max;
  }

  public Dimension getMinimumSize() {
    return computeSize(new Transform<JComponent, Dimension>() {
      public Dimension transform(JComponent component) {
        return component.getMinimumSize();
      }
    }, 1);
  }

  public Dimension getPreferredSize() {
    return computeSize(new Transform<JComponent, Dimension>() {
      public Dimension transform(JComponent component) {
        return component.getPreferredSize();
      }
    }, 3);
  }

  private Dimension computeSize(Transform<JComponent, Dimension> transform, int tabCount) {
    Dimension size = new Dimension();
    for (TabInfo each : myVisibleInfos) {
      final JComponent c = each.getComponent();
      if (c != null) {
        final Dimension eachSize = transform.transform(c);
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
      size.height += myBorder.getTabBorderSize();
    }
    else {
      size.width += myBorder.getTabBorderSize();
    }

    return size;
  }

  public int getTabCount() {
    return getTabs().size();
  }

  @NotNull
  public JBTabsPresentation getPresentation() {
    return this;
  }

  public ActionCallback removeTab(final TabInfo info) {
    return removeTab(info, null, true);
  }

  public ActionCallback removeTab(final TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus) {
    return removeTab(info, forcedSelectionTransfer, transferFocus, false);
  }

  private ActionCallback removeTab(TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus, boolean isDropTarget) {
    if (!isDropTarget) {
      if (info == null || !getTabs().contains(info)) return new ActionCallback.Done();
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
      _setSelected(toSelect, transferFocus).doWhenProcessed(new Runnable() {
        public void run() {
          removeDeferred().notifyWhenDone(result);
        }
      });
    }
    else {
      processRemove(info, true);
      removeDeferred().notifyWhenDone(result);
    }

    if (myVisibleInfos.isEmpty()) {
      removeDeferredNow();
    }

    revalidateAndRepaint(true);

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

    myVisibleInfos.remove(info);
    myHiddenInfos.remove(info);
    myInfo2Label.remove(info);
    myInfo2Toolbar.remove(info);
    resetTabsCache();

    updateAll(false, false);

    // avoid leaks
    myLastPaintedSelection = null;
  }

  @Nullable
  public TabInfo findInfo(Component component) {
    for (TabInfo each : getTabs()) {
      if (each.getComponent() == component) return each;
    }

    return null;
  }

  public TabInfo findInfo(MouseEvent event) {
    return findInfo(event, false);
  }

  @Nullable
  private TabInfo findInfo(final MouseEvent event, final boolean labelsOnly) {
    final Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), this);
    return _findInfo(point, labelsOnly);
  }

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
    for (TabInfo each : new ArrayList<TabInfo>(myVisibleInfos)) {
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

  protected void addImpl(final Component comp, final Object constraints, final int index) {
    unqueueFromRemove(comp);

    if (comp instanceof TabLabel) {
      ((TabLabel)comp).apply(myUiDecorator.getDecoration());
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

  public TabsBorder getTabsBorder() {
    return myBorder;
  }

  @NotNull
  public JBTabs addTabMouseListener(@NotNull MouseListener listener) {
    removeListeners();
    myTabMouseListeners.add(listener);
    addListeners();
    return this;
  }

  @NotNull
  public JComponent getComponent() {
    return this;
  }

  public boolean isCycleRoot() {
    return false;
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

  public int getIndexOf(@Nullable final TabInfo tabInfo) {
    return myVisibleInfos.indexOf(tabInfo);
  }

  public boolean isHideTabs() {
    return myHideTabs;
  }

  public void setHideTabs(final boolean hideTabs) {
    if (isHideTabs() == hideTabs) return;

    myHideTabs = hideTabs;

    relayout(true, false);
  }

  public JBTabsPresentation setPaintBorder(int top, int left, int right, int bottom) {
    return myBorder.setPaintBorder(top, left, right, bottom);
  }

  public JBTabsPresentation setTabSidePaintBorder(int size) {
    return myBorder.setTabSidePaintBorder(size);
  }

  static int getBorder(int size) {
    return size == -1 ? 1 : size;
  }

  private boolean isPaintFocus() {
    return myPaintFocus;
  }

  @NotNull
  public JBTabsPresentation setActiveTabFillIn(@Nullable final Color color) {
    if (!isChanged(myActiveTabFillIn, color)) return this;

    myActiveTabFillIn = color;
    revalidateAndRepaint(false);
    return this;
  }

  private static boolean isChanged(Object oldObject, Object newObject) {
    if (oldObject == null && newObject == null) return false;
    return oldObject != null && !oldObject.equals(newObject) || newObject != null && !newObject.equals(oldObject);
  }

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

  public JBTabsPresentation setFocusCycle(final boolean root) {
    setFocusCycleRoot(root);
    return this;
  }


  public JBTabsPresentation setPaintFocus(final boolean paintFocus) {
    myPaintFocus = paintFocus;
    return this;
  }

  private abstract static class BaseNavigationAction extends AnAction {

    private final ShadowAction myShadow;
    private final ActionManager myActionManager;
    private final JBTabsImpl myTabs;

    protected BaseNavigationAction(final String copyFromID, JBTabsImpl tabs, ActionManager mgr) {
      myActionManager = mgr;
      myTabs = tabs;
      myShadow = new ShadowAction(this, myActionManager.getAction(copyFromID), tabs);
      Disposer.register(tabs, myShadow);
      setEnabledInModalContext(true);
    }

    public final void update(final AnActionEvent e) {
      JBTabsImpl tabs = e.getData(NAVIGATION_ACTIONS_KEY);
      e.getPresentation().setVisible(tabs != null);
      if (tabs == null) return;

      tabs = findNavigatableTabs(tabs);
      e.getPresentation().setEnabled(tabs != null);
      if (tabs != null) {
        _update(e, tabs, tabs.myVisibleInfos.indexOf(tabs.getSelectedInfo()));
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
      final int selectedIndex = tabs.myVisibleInfos.indexOf(tabs.getSelectedInfo());
      return tabs.isNavigationVisible() && selectedIndex >= 0 && tabs.myNavigationActionsEnabled;
    }

    public void reconnect(String actionId) {
      myShadow.reconnect(myActionManager.getAction(actionId));
    }

    protected abstract void _update(AnActionEvent e, final JBTabsImpl tabs, int selectedIndex);

    public final void actionPerformed(final AnActionEvent e) {
      JBTabsImpl tabs = e.getData(NAVIGATION_ACTIONS_KEY);
      tabs = findNavigatableTabs(tabs);
      if (tabs == null) return;

      final int index = tabs.myVisibleInfos.indexOf(tabs.getSelectedInfo());
      if (index == -1) return;
      _actionPerformed(e, tabs, index);
    }

    protected abstract void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex);
  }

  private static class SelectNextAction extends BaseNavigationAction {

    private SelectNextAction(JBTabsImpl tabs, ActionManager mgr) {
      super(IdeActions.ACTION_NEXT_TAB, tabs, mgr);
    }

    protected void _update(final AnActionEvent e, final JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledForward(selectedIndex, true) != null);
    }

    protected void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex) {
      tabs.select(tabs.findEnabledForward(selectedIndex, true), true);
    }
  }

  private static class SelectPreviousAction extends BaseNavigationAction {
    private SelectPreviousAction(JBTabsImpl tabs, ActionManager mgr) {
      super(IdeActions.ACTION_PREVIOUS_TAB, tabs, mgr);
    }

    protected void _update(final AnActionEvent e, final JBTabsImpl tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.findEnabledBackward(selectedIndex, true) != null);
    }

    protected void _actionPerformed(final AnActionEvent e, final JBTabsImpl tabs, final int selectedIndex) {
      tabs.select(tabs.findEnabledBackward(selectedIndex, true), true);
    }
  }

  private void disposePopupListener() {
    if (myActivePopup != null) {
      myActivePopup.removePopupMenuListener(myPopupListener);
      myActivePopup = null;
    }
  }

  public JBTabsPresentation setStealthTabMode(final boolean stealthTabMode) {
    myStealthTabMode = stealthTabMode;

    relayout(true, false);

    return this;
  }

  public boolean isStealthTabMode() {
    return myStealthTabMode;
  }

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

  public JBTabsPresentation setSingleRow(boolean singleRow) {
    myLayout = singleRow ? mySingleRowLayout : myTableLayout;

    relayout(true, false);

    return this;
  }

  public JBTabsPresentation setGhostsAlwaysVisible(final boolean visible) {
    myGhostsAlwaysVisible = visible;

    relayout(true, false);

    return this;
  }

  public boolean useSmallLabels() {
    return false;
  }

  public boolean useBoldLabels() {
    return false;
  }

  public boolean hasUnderline() {
    return false;
  }

  public boolean isGhostsAlwaysVisible() {
    return myGhostsAlwaysVisible;
  }

  public boolean isSingleRow() {
    return getEffectiveLayout() == mySingleRowLayout;
  }

  public boolean isSideComponentVertical() {
    return !myHorizontalSide;
  }

  public boolean isSideComponentOnTabs() {
    return mySideComponentOnTabs;
  }

  public TabLayout getEffectiveLayout() {
    if (myLayout == myTableLayout && getTabsPosition() == JBTabsPosition.top) return myTableLayout;
    return mySingleRowLayout;
  }

  public JBTabsPresentation setUiDecorator(@Nullable UiDecorator decorator) {
    myUiDecorator = decorator == null ? ourDefaultDecorator : decorator;
    applyDecoration();
    return this;
  }

  protected void setUI(final ComponentUI newUI) {
    super.setUI(newUI);
    applyDecoration();
  }

  public void updateUI() {
    super.updateUI();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        applyDecoration();

        revalidateAndRepaint(false);
      }
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

  public void sortTabs(Comparator<TabInfo> comparator) {
    Collections.sort(myVisibleInfos, comparator);

    relayout(true, false);
  }

  private boolean isRequestFocusOnLastFocusedComponent() {
    return myRequestFocusOnLastFocusedComponent;
  }

  public JBTabsPresentation setRequestFocusOnLastFocusedComponent(final boolean requestFocusOnLastFocusedComponent) {
    myRequestFocusOnLastFocusedComponent = requestFocusOnLastFocusedComponent;
    return this;
  }


  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (myDataProvider != null) {
      final Object value = myDataProvider.getData(dataId);
      if (value != null) return value;
    }

    if (SwitchProvider.KEY.getName().equals(dataId) && myOwnSwitchProvider) {
      return this;
    }

    if (QuickActionProvider.KEY.getName().equals(dataId)) {
      return this;
    }

    return NAVIGATION_ACTIONS_KEY.is(dataId) ? this : null;
  }

  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();

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

  public DataProvider getDataProvider() {
    return myDataProvider;
  }

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
    @NotNull
    public UiDecoration getDecoration() {
      return new UiDecoration(null, new Insets(0, 4, 0, 5));
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
        final Object done = jc.getClientProperty(LAYOUT_DONE);
        if (!Boolean.TRUE.equals(done)) {
          layout(jc, new Rectangle(0, 0, 0, 0));
        }
      }
    }
  }


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

  @NotNull
  public JBTabsPresentation setTabsPosition(final JBTabsPosition position) {
    myPosition = position;
    relayout(true, false);
    return this;
  }

  public JBTabsPosition getTabsPosition() {
    return myPosition;
  }

  public TimedDeadzone.Length getTabActionsMouseDeadzone() {
    return myTabActionsMouseDeadzone;
  }

  public JBTabsPresentation setTabDraggingEnabled(boolean enabled) {
    myTabDraggingEnabled = enabled;
    return this;
  }

  public boolean isTabDraggingEnabled() {
    return myTabDraggingEnabled;
  }

  public JBTabsPresentation setProvideSwitchTargets(boolean provide) {
    myOwnSwitchProvider = provide;
    return this;
  }

  void reallocate(TabInfo source, TabInfo target) {
    if (source == target || source == null || target == null) return;

    final int targetIndex = myVisibleInfos.indexOf(target);

    myVisibleInfos.remove(source);
    myVisibleInfos.add(targetIndex, source);

    invalidate();
    relayout(true, true);
  }

  boolean isHorizontalTabs() {
    return getTabsPosition() == JBTabsPosition.top || getTabsPosition() == JBTabsPosition.bottom;
  }

  public void putInfo(@NotNull Map<String, String> info) {
    final TabInfo selected = getSelectedInfo();
    if (selected != null) {
      selected.putInfo(info);
    }
  }

  public void setUseBufferedPaint(boolean useBufferedPaint) {
    myUseBufferedPaint = useBufferedPaint;
    revalidate();
    repaint();
  }

  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    ArrayList<SwitchTarget> result = new ArrayList<SwitchTarget>();
    for (TabInfo each : myVisibleInfos) {
      result.add(new TabTarget(each));
    }

    if (originalProvider && mySwitchDelegate != null) {
      List<SwitchTarget> additional = mySwitchDelegate.getTargets(onlyVisible, false);
      if (additional != null) {
        result.addAll(additional);
      }
    }

    return result;
  }


  public SwitchTarget getCurrentTarget() {
    if (mySwitchDelegate != null) {
      SwitchTarget selection = mySwitchDelegate.getCurrentTarget();
      if (selection != null) return selection;
    }

    return new TabTarget(getSelectedInfo());
  }

  private class TabTarget extends ComparableObject.Impl implements SwitchTarget {

    private final TabInfo myInfo;

    private TabTarget(TabInfo info) {
      myInfo = info;
    }

    public ActionCallback switchTo(boolean requestFocus) {
      return select(myInfo, requestFocus);
    }

    public boolean isVisible() {
      return getRectangle() != null;
    }

    public RelativeRectangle getRectangle() {
      TabLabel label = myInfo2Label.get(myInfo);
      if (label.getRootPane() == null) return null;

      Rectangle b = label.getBounds();
      b.x += 2;
      b.width -= 4;
      b.y += 2;
      b.height -= 4;
      return new RelativeRectangle(label.getParent(), b);
    }

    public Component getComponent() {
      return myInfo2Label.get(myInfo);
    }

    @Override
    public String toString() {
      return myInfo.getText();
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return new Object[]{myInfo};
    }
  }

  @Override
  public void resetDropOver(TabInfo tabInfo) {
    if (myDropInfo != null) {
      TabInfo dropInfo = myDropInfo;
      myDropInfo = null;
      myShowDropLocation = true;
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

    BufferedImage img = UIUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
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

  public int getDropInfoIndex() {
    return myDropInfoIndex;
  }

  public boolean isEmptyVisible() {
    return myVisibleInfos.isEmpty();
  }

  public static int getInterTabSpaceLength() {
    return 1;
  }

  @Override
  public String toString() {
    return "JBTabs visible=" + myVisibleInfos + " selected=" + mySelectedInfo;
  }
}
