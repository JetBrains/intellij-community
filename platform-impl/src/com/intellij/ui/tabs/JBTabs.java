package com.intellij.ui.tabs;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class JBTabs extends JComponent implements PropertyChangeListener, TimerListener, DataProvider, PopupMenuListener {

  private static DataKey<JBTabs> NAVIGATION_ACTIONS_KEY = DataKey.create("JBTabs");

  private ActionManagerEx myActionManager;
  private List<TabInfo> myVisibleInfos = new ArrayList<TabInfo>();
  private Set<TabInfo> myHiddenInfos = new HashSet<TabInfo>();

  private TabInfo mySelectedInfo;
  private Map<TabInfo, TabLabel> myInfo2Label = new HashMap<TabInfo, TabLabel>();
  private Map<TabInfo, JComponent> myInfo2Toolbar = new HashMap<TabInfo, JComponent>();
  private Dimension myHeaderFitSize;

  private static final int INNER = 1;

  private List<MouseListener> myTabMouseListeners = new ArrayList<MouseListener>();
  private List<TabsListener> myTabListeners = new ArrayList<TabsListener>();
  private boolean myFocused;

  private ActionGroup myPopupGroup;
  private String myPopupPlace;
  private TabInfo myPopupInfo;
  private DefaultActionGroup myNavigationActions;

  private PopupMenuListener myPopupListener;
  private JPopupMenu myActivePopup;

  private boolean myHorizontalSide = true;
  private LineLayoutData myLastSingRowLayout;

  private boolean myStealthTabMode = false;

  private MoreIcon myMoreIcon = new MoreIcon() {
    protected boolean isActive() {
      return myFocused;
    }

    protected Rectangle getIconRec() {
      return myLastSingRowLayout != null ? myLastSingRowLayout.moreRect : null;
    }
  };
  private JPopupMenu myMorePopup;

  private boolean mySingleRow = true;
  private TableLayoutData myLastTableLayout;

  private boolean myForcedRelayout;
  private UiDecorator myUiDecorator;

  private boolean myPaintBorder = true;
  private boolean myPaintFocus = true;

  private boolean myHideTabs = false;
  private @Nullable Project myProject;

  private boolean myRequestFocusOnLastFocusedComponent = false;
  private boolean myListenerAdded;
  private final Set<TabInfo> myAttractions = new HashSet<TabInfo>();
  private Animator myAnimator;
  private static final String DEFERRED_REMOVE_FLAG = "JBTabs.deferredRemove";
  private List<TabInfo> myAllTabs;

  public JBTabs(@Nullable Project project, ActionManager actionManager, Disposable parent) {
    myProject = project;
    myActionManager = (ActionManagerEx)actionManager;

    myNavigationActions = new DefaultActionGroup();

    if (myActionManager != null) {
      myNavigationActions.add(new SelectNextAction(this));
      myNavigationActions.add(new SelectPreviousAction(this));
    }

    UIUtil.addAwtListener(new AWTEventListener() {
      public void eventDispatched(final AWTEvent event) {
        if (myMorePopup != null) return;
        processFocusChange();
      }
    }, FocusEvent.FOCUS_EVENT_MASK, parent);

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
        if (myLastSingRowLayout != null && myLastSingRowLayout.moreRect != null && myLastSingRowLayout.moreRect.contains(e.getPoint())) {
          showMorePopup(e);
        }
      }
    });

    Disposer.register(parent, new Disposable() {
      public void dispose() {
        removeTimerUpdate();
      }
    });

    myAnimator = new Animator("JBTabs Attractions", 2, 500, true, 0, -1) {
      public void paintNow(final int frame) {
        repaintAttractions();
      }
    };
    myAnimator.setTakInitialDelay(false);

    Disposer.register(parent, myAnimator);
  }

  private void processFocusChange() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) {
      setFocused(false);
      return;
    }

    if (owner == JBTabs.this || SwingUtilities.isDescendingFrom(owner, JBTabs.this)) {
      setFocused(true);
    } else {
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
      relayout(true);
    }
  }

  public void addNotify() {
    super.addNotify();

    if (myActionManager != null && !myListenerAdded) {
      myActionManager.addTimerListener(500, this);
      myListenerAdded = true;
    }
  }

  public void removeNotify() {
    super.removeNotify();

    setFocused(false);
    
    removeTimerUpdate();
  }

  private void removeTimerUpdate() {
    if (myActionManager != null && myListenerAdded) {
      myActionManager.removeTimerListener(this);
      myListenerAdded = false;
    }
  }

  public ModalityState getModalityState() {
    return ModalityState.stateForComponent(this);
  }

  public void run() {
    updateTabActions();
  }

  public void updateTabActions() {
    for (TabLabel label : myInfo2Label.values()) {
      label.updateTabActions();
    }
  }

  private void showMorePopup(final MouseEvent e) {
    myMorePopup = new JPopupMenu();
    for (final TabInfo each : myVisibleInfos) {
      final JCheckBoxMenuItem item = new JCheckBoxMenuItem(each.getText());
      myMorePopup.add(item);
      if (getSelectedInfo() == each) {
        item.setSelected(true);
      }
      item.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          setSelected(each, true);
        }
      });
    }

    myMorePopup.addPopupMenuListener(new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      }

      public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        myMorePopup = null;
      }

      public void popupMenuCanceled(final PopupMenuEvent e) {
        myMorePopup = null;
      }
    });

    myMorePopup.show(this, e.getX(), e.getY());
  }


  private JComponent getToFocus() {
    final TabInfo info = getSelectedInfo();

    JComponent toFocus = null;

    if (isRequestFocusOnLastFocusedComponent() && info.getLastFocusOwner() != null && !isMyChildIsFocusedNow()) {
      toFocus = info.getLastFocusOwner();
    }

    if (toFocus == null && (info == null || info.getPreferredFocusableComponent() == null)) {
      return null;
    }


    if (toFocus == null) {
      toFocus = info.getPreferredFocusableComponent();
      final JComponent policyToFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(toFocus);
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

  private JBTabs findTabs(Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof JBTabs) {
        return (JBTabs)eachParent;
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }


  public TabInfo addTab(TabInfo info, int index) {
    info.getChangeSupport().addPropertyChangeListener(this);
    final TabLabel label = new TabLabel(info);
    myInfo2Label.put(info, label);

    if (index < 0) {
      myVisibleInfos.add(info);
    }
    else if (index > myVisibleInfos.size() - 1) {
      myVisibleInfos.add(info);
    }
    else {
      myVisibleInfos.add(index, info);
    }

    myAllTabs = null;

    add(label);

    updateText(info);
    updateIcon(info);
    updateSideComponent(info);
    updateTabActions(info);

    updateAll(false);

    if (info.isHidden()) {
      updateHiding();
    }


    return info;
  }


  public TabInfo addTab(TabInfo info) {
    return addTab(info, -1);
  }

  public ActionGroup getPopupGroup() {
    return myPopupGroup;
  }

  public String getPopupPlace() {
    return myPopupPlace;
  }

  public void setPopupGroup(final ActionGroup popupGroup, String place) {
    myPopupGroup = popupGroup;
    myPopupPlace = place;
  }

  private void updateAll(final boolean forcedRelayout) {
    mySelectedInfo = getSelectedInfo();
    removeDeferred(updateContainer(forcedRelayout));
    updateListeners();
    updateTabActions();
  }

  private boolean isMyChildIsFocusedNow() {
    final Component owner = getFocusOwner();
    return owner != null && SwingUtilities.isDescendingFrom(owner, this);
  }

  @Nullable
  private JComponent getFocusOwner() {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return (JComponent)(owner instanceof JComponent ? owner : null);
  }

  public ActionCallback select(TabInfo info, boolean requestFocus) {
    return _setSelected(info, requestFocus);
  }

  public void setSelected(final TabInfo info, final boolean requestFocus) {
    _setSelected(info, requestFocus);
  }

  private ActionCallback _setSelected(final TabInfo info, final boolean requestFocus) {
    if (myRequestFocusOnLastFocusedComponent && mySelectedInfo != null) {
      if (isMyChildIsFocusedNow()) {
        mySelectedInfo.setLastFocusOwner(getFocusOwner());
      }
    }

    TabInfo oldInfo = mySelectedInfo;
    mySelectedInfo = info;
    final TabInfo newInfo = getSelectedInfo();

    final Component deferredRemove = updateContainer(false);

    if (oldInfo != newInfo) {
      for (TabsListener eachListener : myTabListeners) {
        if (eachListener != null) {
          eachListener.selectionChanged(oldInfo, newInfo);
        }
      }
    }

    if (requestFocus) {
      final JComponent toFocus = getToFocus();
      if (myProject != null && toFocus != null) {
        final ActionCallback result = new ActionCallback();
        ToolWindowManager.getInstance(myProject).requestFocus(new ActionCallback.Runnable() {
          public ActionCallback run() {
            toFocus.requestFocus();
            return new ActionCallback.Done();
          }
        }, true).doWhenProcessed(new Runnable() {
          public void run() {
            removeDeferred(deferredRemove).notifyWhenDone(result);
          }
        });
        return result;
      }
      else {
        requestFocus();
        return removeDeferred(deferredRemove);
      }
    } else {
      return removeDeferred(deferredRemove);
    }
  }

  private ActionCallback removeDeferred(final Component deferredRemove) {
    final ActionCallback callback = new ActionCallback();
    if (deferredRemove != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (isForDeferredRemove(deferredRemove)) {
            remove(deferredRemove);
          }
          callback.setDone();
        }
      });
    } else {
      callback.setDone();
    }

    return callback;
  }

  private boolean isForDeferredRemove(Component c) {
    if (c instanceof JComponent) {
      if (((JComponent)c).getClientProperty(DEFERRED_REMOVE_FLAG) == null) return false;

      if (mySelectedInfo != null && mySelectedInfo.getComponent() == c) {
        return false;
      } else {
        return true;
      }

    }

    return false;
  }

  private void setForDeferredRemove(Component c, boolean toRemove) {
    if (c instanceof JComponent) {
      ((JComponent)c).putClientProperty(DEFERRED_REMOVE_FLAG, toRemove ? Boolean.TRUE : null);
    }
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    final TabInfo tabInfo = (TabInfo)evt.getSource();
    if (TabInfo.ACTION_GROUP.equals(evt.getPropertyName())) {
      updateSideComponent(tabInfo);
    }
    else if (TabInfo.TEXT.equals(evt.getPropertyName())) {
      updateText(tabInfo);
    }
    else if (TabInfo.ICON.equals(evt.getPropertyName())) {
      updateIcon(tabInfo);
    }
    else if (TabInfo.ALERT_STATUS.equals(evt.getPropertyName())) {
      boolean start = ((Boolean)evt.getNewValue()).booleanValue();
      updateAttraction(tabInfo, start);
    }
    else if (TabInfo.TAB_ACTION_GROUP.equals(evt.getPropertyName())) {
      updateTabActions(tabInfo);
    } else if (TabInfo.HIDDEN.equals(evt.getPropertyName())) {
      updateHiding();
    }

    relayout(false);
  }

  private void updateHiding() {
    boolean update = false;

    Iterator<TabInfo> visible = myVisibleInfos.iterator();
    while (visible.hasNext()) {
      TabInfo each = visible.next();
      if (each.isHidden() && !myHiddenInfos.contains(each)) {
        myHiddenInfos.add(each);
        visible.remove();
        update = true;
      }
    }


    Iterator<TabInfo> hidden = myHiddenInfos.iterator();
    while (hidden.hasNext()) {
      TabInfo each = hidden.next();
      if (!each.isHidden() && myHiddenInfos.contains(each)) {
        myVisibleInfos.add(each);
        hidden.remove();
        update = true;
      }
    }


    if (update) {
      myAllTabs = null;
      if (mySelectedInfo != null && myHiddenInfos.contains(mySelectedInfo)) {
        mySelectedInfo = getToSelectOnRemoveOf(mySelectedInfo);
      }
      updateAll(true);
    }
  }

  private void updateIcon(final TabInfo tabInfo) {
    myInfo2Label.get(tabInfo).setIcon(tabInfo.getIcon());
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
    else if (!start && myAttractions.size() == 0) {
      myAnimator.suspend();
      repaintAttractions();
    }
  }

  private void updateText(final TabInfo tabInfo) {
    myInfo2Label.get(tabInfo).setText(tabInfo.getText());
  }

  private void updateSideComponent(final TabInfo tabInfo) {
    final JComponent old = myInfo2Toolbar.get(tabInfo);
    if (old != null) {
      remove(old);
    }
    final JComponent toolbar = createToolbarComponent(tabInfo);
    if (toolbar != null) {
      myInfo2Toolbar.put(tabInfo, toolbar);
      add(toolbar);
    }
  }

  private void updateTabActions(final TabInfo info) {
    myInfo2Label.get(info).setTabActions(info.getTabLabelActions());
  }

  @Nullable
  public TabInfo getSelectedInfo() {
    if (!myVisibleInfos.contains(mySelectedInfo)) {
      mySelectedInfo = null;
    }
    return mySelectedInfo != null ? mySelectedInfo : (myVisibleInfos.size() > 0 ? myVisibleInfos.get(0) : null);
  }

  @Nullable
  private TabInfo getToSelectOnRemoveOf(TabInfo info) {
    if (!myVisibleInfos.contains(info)) return null;
    if (mySelectedInfo != info) return null;

    if (myVisibleInfos.size() == 1) return null;

    int index = myVisibleInfos.indexOf(info);
    if (index > 0) return myVisibleInfos.get(index - 1);
    if (index < myVisibleInfos.size() - 1) return myVisibleInfos.get(index + 1);

    return null;
  }

  protected JComponent createToolbarComponent(final TabInfo tabInfo) {
    return new Toolbar(tabInfo);
  }

  public TabInfo getTabAt(final int tabIndex) {
    return getTabs().get(tabIndex);
  }

  public List<TabInfo> getTabs() {
    if (myAllTabs != null) return myAllTabs;

    ArrayList<TabInfo> result = new ArrayList<TabInfo>();
    result.addAll(myVisibleInfos);
    result.addAll(myHiddenInfos);

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
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPopupInfo = null;
      }
    });
  }

  private class Toolbar extends JPanel {
    public Toolbar(TabInfo info) {
      setLayout(new BorderLayout());

      final ActionGroup group = info.getGroup();
      final JComponent side = info.getSideComponent();

      if (group != null) {
        final String place = info.getPlace();
        final JComponent actionToolbar =
          myActionManager.createActionToolbar(place != null ? place : ActionPlaces.UNKNOWN, group, myHorizontalSide).getComponent();
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
  }

  class LineLayoutData {
    Dimension laayoutSize = getSize();
    int contentCount = getTabCount();
    int eachX;
    int xAddin;
    int requiredWidth;
    int toFitWidth;
    List<TabInfo> toLayout = new ArrayList<TabInfo>();
    List<TabInfo> toDrop = new ArrayList<TabInfo>();
    int moreRectWidth = myMoreIcon.getIconWidth() + 6;
    Rectangle moreRect;
    public boolean displayedHToolbar;
    public int yComp;
  }

  class TableLayoutData {
    List<TableRow> table = new ArrayList<TableRow>();
    public Rectangle toFitRec;
  }

  private class TableRow {

    private List<TabInfo> myColumns = new ArrayList<TabInfo>();
    private int width;

    void add(TabInfo info) {
      myColumns.add(info);
      width += myInfo2Label.get(info).getPreferredSize().width;
    }

  }

  public void doLayout() {
    try {
      final Max max = computeMaxSize();
      myHeaderFitSize =
        new Dimension(getSize().width, myHorizontalSide ? Math.max(max.myLabel.height, max.myToolbar.height) : max.myLabel.height);

      if (mySingleRow) {
        layoutSingleRow();
        myLastTableLayout = null;
      }
      else {
        layoutTable();
        myLastSingRowLayout = null;
      }

      if (isStealthModeEffective()) {
        final JBTabs.TabLabel label = myInfo2Label.get(getSelectedInfo());
        final Rectangle bounds = label.getBounds();
        final Insets insets = getLayoutInsets();
        label.setBounds(bounds.x, bounds.y, getWidth() - insets.right - insets.left, bounds.height);
      }

    }
    finally {
      myForcedRelayout = false;
    }
  }

  private void layoutTable() {
    resetLayout(true);
    final TableLayoutData data = computeLayoutTable();
    final Insets insets = getLayoutInsets();
    int eachY = insets.top, eachX;
    for (TableRow eachRow : data.table) {
      eachX = insets.left;
      for (int i = 0; i < eachRow.myColumns.size(); i++) {
        TabInfo tabInfo = eachRow.myColumns.get(i);
        final TabLabel label = myInfo2Label.get(tabInfo);
        int width = label.getPreferredSize().width;
        label.setBounds(eachX, eachY, width, myHeaderFitSize.height);
        eachX += width;
      }
      eachY += myHeaderFitSize.height;
    }

    if (getSelectedInfo() != null) {
      final JComponent selectedToolbar = myInfo2Toolbar.get(getSelectedInfo());

      int xAddin = 0;
      if (!myHorizontalSide && selectedToolbar != null) {
        xAddin = selectedToolbar.getPreferredSize().width + 1;
        selectedToolbar
          .setBounds(insets.left + 1, eachY + 1, selectedToolbar.getPreferredSize().width, getHeight() - eachY - insets.bottom - 2);
      }

      layoutComp(xAddin, eachY, getSelectedInfo().getComponent());
    }

    myLastTableLayout = data;
  }

  private TableLayoutData computeLayoutTable() {
    final TableLayoutData data = new TableLayoutData();

    final Insets insets = getLayoutInsets();
    data.toFitRec =
      new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
    int eachRow = 0, eachX = data.toFitRec.x;
    TableRow eachTableRow = new TableRow();
    data.table.add(eachTableRow);

    int selectedRow = -1;
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel eachLabel = myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width <= data.toFitRec.getMaxX()) {
        eachTableRow.add(eachInfo);
        if (getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
        eachX += size.width;
      }
      else {
        eachTableRow = new TableRow();
        data.table.add(eachTableRow);
        eachRow++;
        eachX = insets.left;
        eachTableRow.add(eachInfo);
        if (getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
        if (eachX + size.width <= data.toFitRec.getMaxX()) {
          eachX += size.width;
        }
        else {
          eachTableRow = new TableRow();
          eachRow++;
          eachX = insets.left;
        }
      }
    }

    List<TableRow> toMove = new ArrayList<TableRow>();
    for (int i = selectedRow + 1; i < data.table.size(); i++) {
      toMove.add(data.table.get(i));
    }

    for (TableRow eachMove : toMove) {
      data.table.remove(eachMove);
      data.table.add(0, eachMove);
    }


    return data;
  }

  private void layoutSingleRow() {
    final TabInfo selected = getSelectedInfo();
    final JComponent selectedToolbar = myInfo2Toolbar.get(selected);

    LineLayoutData data = new LineLayoutData();
    boolean layoutLabels = true;

    if (!myForcedRelayout &&
        myLastSingRowLayout != null &&
        myLastSingRowLayout.contentCount == getTabCount() &&
        myLastSingRowLayout.laayoutSize.equals(getSize())) {
      for (TabInfo each : myVisibleInfos) {
        if (getSelectedInfo() == each) {
          final TabLabel eachLabel = myInfo2Label.get(each);
          if (eachLabel.getBounds().width != 0) {
            data = myLastSingRowLayout;
            layoutLabels = false;
          }
        }
      }
    }


    Insets insets = getLayoutInsets();

    resetLayout(layoutLabels || isHideTabs());


    if (isSideComponentVertical() && selectedToolbar != null) {
      data.xAddin = selectedToolbar.getPreferredSize().width + 1;
    }

    if (layoutLabels && !isHideTabs()) {
      data.eachX = insets.left;

      recomputeToLayout(selectedToolbar, data, insets);

      layoutLabels(data, insets);

      if (data.toDrop.size() > 0) {
        data.moreRect = new Rectangle(data.eachX, insets.top, data.moreRectWidth - 1, myHeaderFitSize.height - 1);
      }
    }

    data.yComp = isHideTabs() ? insets.top : myHeaderFitSize.height + insets.top;
    if (selectedToolbar != null) {
      if (!isSideComponentVertical() && !isHideTabs()) {
        int toolbarX = data.eachX + getToolbarInset() + (data.moreRect != null ? data.moreRect.width : 0);
        selectedToolbar.setBounds(toolbarX, insets.top, getSize().width - insets.left - toolbarX, myHeaderFitSize.height - 1);
      }
      else if (isSideComponentVertical()) {
        selectedToolbar.setBounds(insets.left + 1, data.yComp + 1, selectedToolbar.getPreferredSize().width,
                                  getSize().height - data.yComp - insets.bottom - 2);
      }
    }


    if (selected != null) {
      final JComponent comp = selected.getComponent();
      layoutComp(data.xAddin, data.yComp, comp);
    }


    if (data.toLayout.size() > 0 && myVisibleInfos.size() > 0) {
      final int left = myVisibleInfos.indexOf(data.toLayout.get(0));
      final int right = myVisibleInfos.indexOf(data.toLayout.get(data.toLayout.size() - 1));
      myMoreIcon.setPaintedIcons(left > 0, right < myVisibleInfos.size() - 1);
    }
    else {
      myMoreIcon.setPaintedIcons(false, false);
    }


    myLastSingRowLayout = data;
  }

  private void layoutComp(int xAddin, int yComp, final JComponent comp) {
    final Insets insets = getLayoutInsets();
    comp.setBounds(insets.left + INNER + xAddin, yComp + 1, getWidth() - insets.left - insets.right - INNER * 2 - xAddin,
                   getHeight() - insets.bottom - yComp - 2);
  }

  private Insets getLayoutInsets() {
    Insets insets = getInsets();
    if (insets == null) {
      insets = new Insets(0, 0, 0, 0);
    }
    return insets;
  }

  private void layoutLabels(final LineLayoutData data, final Insets insets) {
    boolean reachedBounds = false;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      final Dimension eachSize = label.getPreferredSize();

      if (data.eachX + eachSize.width <= data.toFitWidth) {
        label.setBounds(data.eachX, insets.top, eachSize.width, myHeaderFitSize.height);
        data.eachX += eachSize.width;
        data.eachX++;
      }
      else {
        if (!reachedBounds) {
          final int width = getWidth() - data.eachX - data.moreRectWidth;
          label.setBounds(data.eachX, insets.top, width, myHeaderFitSize.height);
          data.eachX += width;
          data.eachX++;
        }
        else {
          label.setBounds(0, 0, 0, 0);
        }
      }
    }
  }

  private void recomputeToLayout(final JComponent selectedToolbar, final LineLayoutData data, final Insets insets) {
    final int toolbarInset = getToolbarInset();
    data.displayedHToolbar = myHorizontalSide && selectedToolbar != null;
    data.toFitWidth = getWidth() - insets.left - insets.right - (data.displayedHToolbar ? toolbarInset : 0);
    for (TabInfo eachInfo : myVisibleInfos) {
      data.requiredWidth += myInfo2Label.get(eachInfo).getPreferredSize().width;
      data.toLayout.add(eachInfo);
    }

    while (true) {
      if (data.requiredWidth <= data.toFitWidth - data.eachX) break;
      if (data.toLayout.size() == 0) break;

      final TabInfo first = data.toLayout.get(0);
      final TabInfo last = data.toLayout.get(data.toLayout.size() - 1);
      if (first != getSelectedInfo()) {
        processDrop(data, first);
      }
      else if (last != getSelectedInfo()) {
        processDrop(data, last);
      }
      else {
        if (data.toDrop.size() > 0) {
        }
        break;
      }
    }
  }

  private int getToolbarInset() {
    return getArcSize() + 1;
  }

  private void resetLayout(boolean resetLabels) {
    for (TabInfo each : myVisibleInfos) {
      reset(each, resetLabels);
    }

    for (TabInfo each : myHiddenInfos) {
      reset(each, resetLabels);
    }
  }

  private void reset(final TabInfo each, final boolean resetLabels) {
    final JComponent c = each.getComponent();
    if (c != null) {
      c.setBounds(0, 0, 0, 0);
    }

    final JComponent toolbar = myInfo2Toolbar.get(each);
    if (toolbar != null) {
      toolbar.setBounds(0, 0, 0, 0);
    }

    if (resetLabels) {
      myInfo2Label.get(each).setBounds(0, 0, 0, 0);
    }
  }

  private void processDrop(final LineLayoutData data, final TabInfo info) {
    data.requiredWidth -= myInfo2Label.get(info).getPreferredSize().width;
    data.toDrop.add(info);
    data.toLayout.remove(info);
    if (data.toDrop.size() == 1) {
      data.toFitWidth -= data.moreRectWidth;
    }
  }

  private int getArcSize() {
    return 4;
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);


    final TabInfo selected = getSelectedInfo();
    if (selected == null) return;


    final JBTabs.TabLabel selectedLabel = myInfo2Label.get(selected);
    if (selectedLabel == null) return;

    Rectangle selectedTabBounds = selectedLabel.getBounds();


    final GraphicsConfig config = new GraphicsConfig(g);
    config.setAntialiasing(true);

    Graphics2D g2d = (Graphics2D)g;

    int arc = getArcSize();

    final GeneralPath path = new GeneralPath();
    Insets insets = getLayoutInsets();
    final int bottomY = (int)selectedTabBounds.getMaxY();
    final int topY = selectedTabBounds.y;
    int leftX = selectedTabBounds.x;

    int rightX = selectedTabBounds.x + selectedTabBounds.width;

    path.moveTo(insets.left, bottomY);
    path.lineTo(leftX, bottomY);
    path.lineTo(leftX, topY + arc);
    path.quadTo(leftX, topY, leftX + arc, topY);

    int lastX = getWidth() - insets.right - 1;

    if (isStealthModeEffective()) {
      path.lineTo(lastX - arc, topY);
      path.quadTo(lastX, topY, lastX, topY + arc);
      path.lineTo(lastX, bottomY);
    }
    else {
      path.lineTo(rightX - arc, topY);
      path.quadTo(rightX, topY, rightX, topY + arc);
      path.lineTo(rightX, bottomY - arc);
      path.quadTo(rightX, bottomY, rightX + arc, bottomY);
    }

    path.lineTo(lastX, bottomY);
    path.closePath();

    final Color from;
    final Color to;
    final int alpha;
    final boolean paintFocused = myPaintFocus && (myFocused || myActivePopup != null);
    if (paintFocused) {
      from = UIUtil.getFocusedFillColor();
      to = UIUtil.getFocusedFillColor();
    }
    else {
      alpha = 150;
      from = UIUtil.toAlpha(UIUtil.getPanelBackgound().brighter(), alpha);
      to = UIUtil.toAlpha(UIUtil.getPanelBackgound(), alpha);
    }


    g2d.setPaint(new GradientPaint(selectedTabBounds.x, topY, from, selectedTabBounds.x, bottomY, to));

    if (!isHideTabs()) {
      g2d.fill(path);
    }

    if (paintFocused) {
      g2d.setColor(UIUtil.getFocusedBoundsColor());
    }
    else {
      g2d.setColor(CaptionPanel.CNT_ACTIVE_COLOR.darker());
    }

    if (!isHideTabs()) {
      g2d.draw(path);
    }

    if (myPaintBorder) {
      if (isHideTabs()) {
        g2d.drawRect(insets.left, insets.top, getWidth() - insets.left - insets.right - 1, getHeight() - insets.bottom - insets.top - 1);
      }
      else {
        g2d.drawRect(insets.left, bottomY, getWidth() - insets.left - insets.right - 1, getHeight() - bottomY - insets.bottom - 1);
      }
    }

    config.restore();
  }

  private boolean isStealthModeEffective() {
    return myStealthTabMode && getTabCount() == 1 && isSideComponentVertical();
  }

  private boolean isNavigationVisible() {
    if (myStealthTabMode && getTabCount() == 1) return false;
    return myVisibleInfos.size() > 0;
  }


  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);


    if (isSingleRow() && myLastSingRowLayout != null) {
      final List<TabInfo> infos = myLastSingRowLayout.toLayout;
      for (int i = 1; i < infos.size(); i++) {
        final TabInfo each = infos.get(i);
        if (getSelectedInfo() != each && getSelectedInfo() != infos.get(i - 1)) {
          drawSeparator(g, each);
        }
      }
    }
    else if (!isSingleRow() && myLastTableLayout != null) {
      final List<TableRow> table = myLastTableLayout.table;
      for (TableRow eachRow : table) {
        final List<TabInfo> infos = eachRow.myColumns;
        for (int i = 1; i < infos.size(); i++) {
          final TabInfo each = infos.get(i);
          if (getSelectedInfo() != each && getSelectedInfo() != infos.get(i - 1)) {
            drawSeparator(g, each);
          }
        }
      }
    }

    myMoreIcon.paintIcon(this, g);
  }

  private void drawSeparator(Graphics g, TabInfo info) {
    final JBTabs.TabLabel label = myInfo2Label.get(info);
    if (label == null) return;
    final Rectangle bounds = label.getBounds();

    final double height = bounds.height * 0.85d;
    final double delta = bounds.height - height;

    final int y1 = (int)(bounds.y + delta) + 1;
    final int x1 = bounds.x;
    final int y2 = (int)(bounds.y + bounds.height - delta);
    UIUtil.drawVDottedLine((Graphics2D)g, x1, y1, y2, getBackground(), Color.gray);
  }

  private Max computeMaxSize() {
    Max max = new Max();
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      max.myLabel.height = Math.max(max.myLabel.height, label.getPreferredSize().height);
      max.myLabel.width = Math.max(max.myLabel.width, label.getPreferredSize().width);
      final JComponent toolbar = myInfo2Toolbar.get(eachInfo);
      if (toolbar != null) {
        max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.getPreferredSize().height);
        max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.getPreferredSize().width);
      }
    }

    max.myToolbar.height++;

    return max;
  }

  public int getTabCount() {
    return getTabs().size();
  }

  public void removeTab(final JComponent component) {
    removeTab(findInfo(component));
  }

  public void removeTab(TabInfo info) {
    removeTab(info, true);
  }

  public void removeTab(final TabInfo info, boolean transferFocus) {
    if (info == null) return;

    TabInfo toSelect = transferFocus ? getToSelectOnRemoveOf(info) : null;


    if (toSelect != null) {
      final JComponent deferred = processRemove(info);
      _setSelected(toSelect, true).doWhenDone(new Runnable() {
        public void run() {
          removeDeferred(deferred);
        }
      });
    } else {
      removeDeferred(processRemove(info));
    }
  }

  @Nullable
  private JComponent processRemove(final TabInfo info) {
    remove(myInfo2Label.get(info));
    final JComponent tb = myInfo2Toolbar.get(info);
    if (tb != null) {
      remove(tb);
    }

    JComponent tabComponent = info.getComponent();

    if (!isFocused(tabComponent)) {
      remove(tabComponent);
      tabComponent = null;
    } else {
      setForDeferredRemove(tabComponent, true);
    }

    myVisibleInfos.remove(info);
    myHiddenInfos.remove(info);
    myInfo2Label.remove(info);
    myInfo2Toolbar.remove(info);
    myAllTabs = null;

    updateAll(false);

    return tabComponent;
  }

  public TabInfo findInfo(Component component) {
    for (TabInfo each : getTabs()) {
      if (each.getComponent() == component) return each;
    }

    return null;
  }

  public TabInfo findInfo(String text) {
    if (text == null) return null;

    for (TabInfo each : getTabs()) {
      if (text.equals(each.getText())) return each;
    }

    return null;
  }

  public TabInfo findInfo(MouseEvent event) {
    final Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), this);
    return _findInfo(point, false);
  }

  public TabInfo findTabLabelBy(final Point point) {
    return _findInfo(point, true);
  }

  private TabInfo _findInfo(final Point point, boolean labelsOnly) {
    Component component = findComponentAt(point);
    if (component == null) return null;
    while (component != this || component != null) {
      if (component instanceof TabLabel) {
        return ((TabLabel)component).getInfo();
      }
      else if (!labelsOnly) {
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


  private class Max {
    Dimension myLabel = new Dimension();
    Dimension myToolbar = new Dimension();
  }

  @Nullable
  private Component updateContainer(boolean forced) {
    Component deferredRemove = null;

    for (TabInfo each : myVisibleInfos) {
      final JComponent eachComponent = each.getComponent();
      if (getSelectedInfo() == each && getSelectedInfo() != null) {
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
        if (isFocused(eachComponent)) {
          deferredRemove = eachComponent;
        } else {
          remove(eachComponent);
        }
      }
    }

    if (deferredRemove != null) {
      setForDeferredRemove(deferredRemove, true);
    }


    relayout(forced);

    return deferredRemove;
  }

  protected void addImpl(final Component comp, final Object constraints, final int index) {
    setForDeferredRemove(comp, false);
    super.addImpl(comp, constraints, index);
  }

  private boolean isFocused(JComponent c) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focusOwner != null && (focusOwner == c || SwingUtilities.isDescendingFrom(focusOwner, c));
  }

  private void relayout(boolean forced) {
    if (!myForcedRelayout) {
      myForcedRelayout = forced;
    }
    revalidate();
    repaint();
  }

  ActionManager getActionManager() {
    return myActionManager;
  }

  public void addTabMouseListener(MouseListener listener) {
    removeListeners();
    myTabMouseListeners.add(listener);
    addListeners();
  }

  public void removeTabMouseListener(MouseListener listener) {
    removeListeners();
    myTabMouseListeners.remove(listener);
    addListeners();
  }

  private void addListeners() {
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      for (MouseListener eachListener : myTabMouseListeners) {
        label.addMouseListener(eachListener);
      }
    }
  }

  private void removeListeners() {
    for (TabInfo eachInfo : myVisibleInfos) {
      final TabLabel label = myInfo2Label.get(eachInfo);
      for (MouseListener eachListener : myTabMouseListeners) {
        label.removeMouseListener(eachListener);
      }
    }
  }

  private void updateListeners() {
    removeListeners();
    addListeners();
  }

  public void addListener(TabsListener listener) {
    myTabListeners.add(listener);
  }

  private class TabLabel extends JPanel {
    private JLabel myLabel = new JLabel();
    private TabInfo myInfo;
    private ActionPanel myActionPanel;

    public TabLabel(final TabInfo info) {
      myInfo = info;
      setOpaque(false);
      setLayout(new BorderLayout(myLabel.getIconTextGap() * 2, 0));
      add(myLabel, BorderLayout.CENTER);

      myLabel.setIcon(new LayeredIcon(2));

      addMouseListener(new MouseAdapter() {
        public void mousePressed(final MouseEvent e) {
          if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1 && !e.isPopupTrigger()) {
            setSelected(info, true);
          }
          else {
            handlePopup(e);
          }
        }

        public void mouseClicked(final MouseEvent e) {
          handlePopup(e);
        }

        public void mouseReleased(final MouseEvent e) {
          handlePopup(e);
        }
      });


      setBorder(new EmptyBorder(2, 8, 2, 8));
    }

    private void handlePopup(final MouseEvent e) {
      if (e.getClickCount() != 1 || !e.isPopupTrigger()) return;

      String place = getPopupPlace();
      place = place != null ? place : ActionPlaces.UNKNOWN;
      myPopupInfo = myInfo;

      final DefaultActionGroup toShow = new DefaultActionGroup();
      if (getPopupGroup() != null) {
        toShow.addAll(getPopupGroup());
        toShow.addSeparator();
      }

      Object tabs =
        DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()).getData(NAVIGATION_ACTIONS_KEY.getName());
      if (tabs == JBTabs.this) {
        toShow.addAll(myNavigationActions);
      }

      if (toShow.getChildrenCount() == 0) return;

      myActivePopup = myActionManager.createActionPopupMenu(place, toShow).getComponent();
      myActivePopup.addPopupMenuListener(myPopupListener);

      myActivePopup.addPopupMenuListener(JBTabs.this);
      myActivePopup.show(e.getComponent(), e.getX(), e.getY());
      onPopup(myPopupInfo);
    }


    public void setText(final String text) {
      myLabel.setText(text);
    }

    public void setIcon(final Icon icon) {
      getLayeredIcon().setIcon(icon, 0);
    }

    private LayeredIcon getLayeredIcon() {
      return ((LayeredIcon)myLabel.getIcon());
    }

    public void setAttraction(boolean enabled) {
      getLayeredIcon().setLayerEnabled(1, enabled);
    }

    public boolean isAttractionEnabled() {
      return getLayeredIcon().isLayerEnabled(1);
    }

    public TabInfo getInfo() {
      return myInfo;
    }

    public void apply(UiDecoration decoration) {
      if (decoration.getLabelFont() != null) {
        setFont(decoration.getLabelFont());
      }

      Insets insets = decoration.getLabelInsets();
      if (insets != null) {
        Insets current = getInsets();
        if (current == null) {
          current = new Insets(0, 0, 0, 0);
        }
        setBorder(new EmptyBorder(getValue(current.top, insets.top), getValue(current.left, insets.left),
                                  getValue(current.bottom, insets.bottom), getValue(current.right, insets.right)));
      }
    }

    private int getValue(int curentValue, int newValue) {
      return newValue != -1 ? newValue : curentValue;
    }

    public void setTabActions(ActionGroup group) {
      removeOldActionPanel();

      if (group == null) return;

      myActionPanel = new ActionPanel(myInfo);

      NonOpaquePanel wrapper = new NonOpaquePanel(new GridBagLayout());
      wrapper.add(myActionPanel);

      add(wrapper, BorderLayout.EAST);

      revalidate();
      repaint();
    }

    private void removeOldActionPanel() {
      if (myActionPanel != null) {
        myActionPanel.getParent().remove(myActionPanel);
        myActionPanel = null;
      }
    }

    public void updateTabActions() {
      if (myActionPanel != null) {
        myActionPanel.update();
      }
    }

    public boolean repaintAttraction() {
      if (!myAttractions.contains(myInfo)) {
        if (getLayeredIcon().isLayerEnabled(1)) {
          getLayeredIcon().setLayerEnabled(1, false);
          getLayeredIcon().setIcon(null, 1);
          return true;
        }
        return false;
      }

      boolean needsUpdate = false;

      if (getLayeredIcon().getIcon(1) != myInfo.getAlertIcon()) {
        getLayeredIcon().setIcon(myInfo.getAlertIcon(), 1);
        needsUpdate = true;
      }

      int maxInitialBlinkCount = 5;
      int maxRefireBlinkCount = maxInitialBlinkCount + 2;
      if (myInfo.getBlinkCount() < maxInitialBlinkCount && myInfo.isAlertRequested()) {
        getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
        if (myInfo.getBlinkCount() == 0) {
          needsUpdate = true;
        }
        myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

        if (myInfo.getBlinkCount() == maxInitialBlinkCount) {
          myInfo.resetAlertRequest();
        }

        repaint();
      }
      else {
        if (myInfo.getBlinkCount() < maxRefireBlinkCount && myInfo.isAlertRequested()) {
          getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
          myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

          if (myInfo.getBlinkCount() == maxRefireBlinkCount) {
            myInfo.setBlinkCount(maxInitialBlinkCount);
            myInfo.resetAlertRequest();
          }

          repaint();
        }
        else {
          needsUpdate = !getLayeredIcon().isLayerEnabled(1);
          getLayeredIcon().setLayerEnabled(1, true);
        }
      }

      return needsUpdate;
    }
  }

  protected void onPopup(final TabInfo popupInfo) {
  }

  public void setFocused(final boolean focused) {
    myFocused = focused;
    repaint();
  }

  public boolean isHideTabs() {
    return myHideTabs;
  }

  public void setHideTabs(final boolean hideTabs) {
    if (isHideTabs() == hideTabs) return;

    myHideTabs = hideTabs;

    relayout(true);
  }

  public boolean isPaintBorder() {
    return myPaintBorder;
  }

  public void setPaintBorder(final boolean paintBorder) {
    myPaintBorder = paintBorder;
    repaint();
  }

  public boolean isPaintFocus() {
    return myPaintFocus;
  }

  public void setPaintFocus(final boolean paintFocus) {
    myPaintFocus = paintFocus;
  }

  private static abstract class BaseNavigationAction extends AnAction {

    private ShadowAction myShadow;

    protected BaseNavigationAction(final String copyFromID, JComponent c) {
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(copyFromID), c);
    }

    public final void update(final AnActionEvent e) {
      JBTabs tabs = e.getData(NAVIGATION_ACTIONS_KEY);
      e.getPresentation().setVisible(tabs != null);
      if (tabs == null) return;

      final int selectedIndex = tabs.myVisibleInfos.indexOf(tabs.getSelectedInfo());
      final boolean enabled = tabs.myVisibleInfos.size() > 0 && selectedIndex >= 0;
      e.getPresentation().setEnabled(enabled);
      if (enabled) {
        _update(e, tabs, selectedIndex);
      }
    }

    protected abstract void _update(AnActionEvent e, final JBTabs tabs, int selectedIndex);

    public final void actionPerformed(final AnActionEvent e) {
      JBTabs tabs = e.getData(NAVIGATION_ACTIONS_KEY);
      if (tabs == null) return;

      final int index = tabs.myVisibleInfos.indexOf(tabs.getSelectedInfo());
      if (index == -1) return;
      _actionPerformed(e, tabs, index);
    }

    protected abstract void _actionPerformed(final AnActionEvent e, final JBTabs tabs, final int selectedIndex);
  }

  private static class SelectNextAction extends BaseNavigationAction {

    public SelectNextAction(JComponent c) {
      super(IdeActions.ACTION_NEXT_TAB, c);
    }

    protected void _update(final AnActionEvent e, final JBTabs tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.myVisibleInfos.size() > 0 && selectedIndex < tabs.myVisibleInfos.size() - 1);
    }

    protected void _actionPerformed(final AnActionEvent e, final JBTabs tabs, final int selectedIndex) {
      tabs.setSelected(tabs.myVisibleInfos.get(selectedIndex + 1), true);
    }
  }

  private static class SelectPreviousAction extends BaseNavigationAction {
    public SelectPreviousAction(JComponent c) {
      super(IdeActions.ACTION_PREVIOUS_TAB, c);
    }

    protected void _update(final AnActionEvent e, final JBTabs tabs, int selectedIndex) {
      e.getPresentation().setEnabled(tabs.myVisibleInfos.size() > 0 && selectedIndex > 0);
    }

    protected void _actionPerformed(final AnActionEvent e, final JBTabs tabs, final int selectedIndex) {
      tabs.setSelected(tabs.myVisibleInfos.get(selectedIndex - 1), true);
    }
  }

  private void disposePopupListener() {
    if (myActivePopup != null) {
      myActivePopup.removePopupMenuListener(myPopupListener);
      myActivePopup = null;
    }
  }

  public void setStealthTabMode(final boolean stealthTabMode) {
    myStealthTabMode = stealthTabMode;

    relayout(true);
  }

  public boolean isStealthTabMode() {
    return myStealthTabMode;
  }

  public void setSideComponentVertical(final boolean vertical) {
    myHorizontalSide = !vertical;

    for (TabInfo each : myVisibleInfos) {
      each.getChangeSupport().firePropertyChange(TabInfo.ACTION_GROUP, "new1", "new2");
    }


    relayout(true);
  }

  public void setSingleRow(boolean singleRow) {
    mySingleRow = singleRow;

    relayout(true);
  }

  public boolean isSingleRow() {
    return mySingleRow;
  }

  public boolean isSideComponentVertical() {
    return !myHorizontalSide;
  }

  public void setUiDecorator(UiDecorator decorator) {
    myUiDecorator = decorator;
    applyDecoration();
  }

  protected void setUI(final ComponentUI newUI) {
    super.setUI(newUI);
    applyDecoration();
  }

  public void updateUI() {
    super.updateUI();
    applyDecoration();
  }

  private void applyDecoration() {
    if (myUiDecorator != null) {
      UiDecoration uiDecoration = myUiDecorator.getDecoration();
      for (TabLabel each : myInfo2Label.values()) {
        each.apply(uiDecoration);
      }
    }

    relayout(true);
  }

  public void sortTabs(Comparator<TabInfo> comparator) {
    Collections.sort(myVisibleInfos, comparator);

    relayout(true);
  }

  public static class UiDecoration {
    private @Nullable Font myLabelFont;
    private @Nullable Insets myLabelInsets;

    public UiDecoration(final Font labelFont, final Insets labelInsets) {
      myLabelFont = labelFont;
      myLabelInsets = labelInsets;
    }

    @Nullable
    public Font getLabelFont() {
      return myLabelFont;
    }

    @Nullable
    public Insets getLabelInsets() {
      return myLabelInsets;
    }
  }

  public boolean isRequestFocusOnLastFocusedComponent() {
    return myRequestFocusOnLastFocusedComponent;
  }

  public void setRequestFocusOnLastFocusedComponent(final boolean requestFocusOnLastFocusedComponent) {
    myRequestFocusOnLastFocusedComponent = requestFocusOnLastFocusedComponent;
  }

  public static interface UiDecorator {
    @NotNull
    UiDecoration getDecoration();
  }


  private class ActionPanel extends NonOpaquePanel {

    private ActionGroup myGroup;
    private List<ActionButton> myButtons = new ArrayList<ActionButton>();
    private TabInfo myTabInfo;

    private ActionPanel(TabInfo tabInfo) {
      myTabInfo = tabInfo;
      myGroup = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
      AnAction[] children = myGroup.getChildren(null);
      setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
      for (AnAction each : children) {
        ActionButton eachButton = new ActionButton(tabInfo, each, tabInfo.getTabActionPlace());
        myButtons.add(eachButton);
        add(eachButton.getComponent());
      }
    }

    public void update() {
      boolean changed = false;
      for (ActionButton each : myButtons) {
        changed |= each.update();
      }

      if (changed) {
        JBTabs.this.relayout(true);
      }
    }

    public Dimension getPreferredSize() {
      for (ActionButton each : myButtons) {
        if (each.getComponent().isVisible()) {
          return super.getPreferredSize();
        }
      }
      return new Dimension(0, 0);
    }
  }

  private class ActionButton extends IconButton implements ActionListener {

    private InplaceButton myButton;
    private Presentation myPrevPresentation;
    private AnAction myAction;
    private String myPlace;
    private TabInfo myTabInfo;

    private ActionButton(TabInfo tabInfo, AnAction action, String place) {
      super(null, action.getTemplatePresentation().getIcon());
      myTabInfo = tabInfo;
      myAction = action;
      myPlace = place;
      myButton = new InplaceButton(this, this);
      myButton.setVisible(false);
    }

    public InplaceButton getComponent() {
      return myButton;
    }

    public boolean update() {
      AnActionEvent event = createAnEvent(null);

      if (event == null) return false;

      myAction.update(event);
      Presentation p = event.getPresentation();
      boolean changed = !areEqual(p, myPrevPresentation);

      if (changed) {
        myButton.setIcon(p.getIcon());
        String tooltipText = AnAction.createTooltipText(p.getText(), myAction);
        myButton.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
        myButton.setVisible(p.isEnabled() && p.isVisible());
      }

      myPrevPresentation = p;

      return changed;
    }

    private boolean areEqual(Presentation p1, Presentation p2) {
      if (p1 == null || p2 == null) return false;

      return ComparableObjectCheck.equals(p1.getText(), p2.getText()) &&
             ComparableObjectCheck.equals(p1.getIcon(), p2.getIcon()) &&
             p1.isEnabled() == p2.isEnabled() &&
             p1.isVisible() == p2.isVisible();

    }

    public void actionPerformed(final ActionEvent e) {
      AnActionEvent event = createAnEvent(null);
      if (event != null) {
        myAction.beforeActionPerformedUpdate(event);
        if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
          myAction.actionPerformed(event);
        }
      }
    }

    @Nullable
    private AnActionEvent createAnEvent(InputEvent e) {
      Presentation presentation = (Presentation)myAction.getTemplatePresentation().clone();
      DataContext context = DataManager.getInstance().getDataContext(myTabInfo.getComponent());
      return new AnActionEvent(e, context, myPlace != null ? myPlace : ActionPlaces.UNKNOWN, presentation, myActionManager, 0);
    }
  }

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (!NAVIGATION_ACTIONS_KEY.getName().equals(dataId)) return null;
    return isNavigationVisible() ? this : null;
  }


  public static void main(String[] args) {
    System.out.println("JBTabs.main");

    IconLoader.activate();

    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final int[] count = new int[1];
    final JBTabs tabs = new JBTabs(null, null, new Disposable() {
      public void dispose() {
      }
    }) {
      protected JComponent createToolbarComponent(final TabInfo tabInfo) {
        final JLabel jLabel = new JLabel("X" + (++count[0])) {
          protected void paintComponent(final Graphics g) {
            super.paintComponent(g);
            System.out.println(tabInfo.getText());
          }
        };
        jLabel.setBorder(new LineBorder(Color.red));
        return jLabel;
      }
    };
    frame.getContentPane().add(tabs, BorderLayout.CENTER);

    JPanel south = new JPanel(new FlowLayout());

    final JCheckBox f = new JCheckBox("Focused");
    f.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setFocused(f.isSelected());
      }
    });
    south.add(f);


    final JCheckBox v = new JCheckBox("Vertical");
    v.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSideComponentVertical(v.isSelected());
      }
    });
    south.add(v);

    final JCheckBox row = new JCheckBox("Single row", true);
    row.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSingleRow(row.isSelected());
      }
    });
    south.add(row);

    final JCheckBox stealth = new JCheckBox("Stealth tab", tabs.isStealthTabMode());
    stealth.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setStealthTabMode(stealth.isSelected());
      }
    });
    south.add(stealth);

    final JCheckBox hide = new JCheckBox("Hide tabs", tabs.isHideTabs());
    hide.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setHideTabs(hide.isSelected());
      }
    });
    south.add(hide);

    frame.getContentPane().add(south, BorderLayout.SOUTH);

    tabs.addListener(new TabsListener() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        System.out.println("TabsWithActions.selectionChanged old=" + oldSelection + " new=" + newSelection);
      }
    });

    tabs.addTab(new TabInfo(new JTree() {
      public void addNotify() {
        super.addNotify();    //To change body of overridden methods use File | Settings | File Templates.
        System.out.println("JBTabs.addNotify");
      }

      public void removeNotify() {
        System.out.println("JBTabs.removeNotify");
        super.removeNotify();    //To change body of overridden methods use File | Settings | File Templates.
      }
    })).setText("Tree1").setActions(new DefaultActionGroup(), null)
      .setIcon(IconLoader.getIcon("/debugger/frame.png"));

    final TabInfo toAnimate1 = new TabInfo(new JTree());
    toAnimate1.setIcon(IconLoader.getIcon("/debugger/console.png"));
    final JCheckBox attract1 = new JCheckBox("Attract 1");
    attract1.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (attract1.isSelected()) {
          toAnimate1.fireAlert();
        }
        else {
          toAnimate1.stopAlerting();
        }
      }
    });
    south.add(attract1);

    final JCheckBox hide1 = new JCheckBox("Hide 1", toAnimate1.isHidden());
    hide1.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.setHidden(!toAnimate1.isHidden());
      }
    });
    south.add(hide1);


    final JButton refire = new JButton("Re-fire attraction");
    refire.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.fireAlert();
      }
    });

    south.add(refire);


    tabs.addTab(toAnimate1).setText("Tree2");
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 1").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 2").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 3").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 4").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 5").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 6").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 7").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 8").setActions(new DefaultActionGroup(), null);
    //tabs.addTab(new TabInfo(new JTable())).setText("Table 9").setActions(new DefaultActionGroup(), null);


    tabs.setBorder(new EmptyBorder(6, 6, 20, 6));

    tabs.setUiDecorator(new UiDecorator() {
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(1, -1, 1, -1));
      }
    });


    frame.setBounds(200, 200, 800, 400);
    frame.show();
  }


}
