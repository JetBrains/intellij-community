// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.intellij.icons.AllIcons.General.CollapseComponent;
import static com.intellij.icons.AllIcons.General.CollapseComponentHover;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static icons.DvcsImplIcons.*;

public class BranchActionGroupPopup extends FlatSpeedSearchPopup {
  private static final DataKey<ListPopupModel> POPUP_MODEL = DataKey.create("VcsPopupModel");
  static final String BRANCH_POPUP = "BranchWidget";
  private Project myProject;
  private MyPopupListElementRenderer myListElementRenderer;
  private boolean myShown;
  private boolean myUserSizeChanged;
  private boolean myInternalSizeChanged;
  private int myMeanRowHeight;
  @Nullable private final String myKey;
  @NotNull private Dimension myPrevSize = JBUI.emptySize();
  //these toolbar buttons can be null for child popup components
  @Nullable private MyToolbarButton myRestoreSizeButton;
  @Nullable private MyToolbarButton mySettingsButton;

  private final List<AnAction> mySettingsActions = ContainerUtil.newArrayList();
  @Nullable private JPanel myTitleToolbarPanel;

  public BranchActionGroupPopup(@NotNull String title,
                                @NotNull Project project,
                                @NotNull Condition<AnAction> preselectActionCondition,
                                @NotNull ActionGroup actions,
                                @Nullable String dimensionKey) {
    super(title, new DefaultActionGroup(actions, createBranchSpeedSearchActionGroup(actions)), SimpleDataContext.getProjectContext(project),
          preselectActionCondition, true);
    myProject = project;
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    myKey = dimensionKey;
    if (myKey != null) {
      Dimension storedSize = WindowStateService.getInstance(myProject).getSizeFor(myProject, myKey);
      if (storedSize != null) {
        //set forced size before component is shown
        setSize(storedSize);
        myUserSizeChanged = true;
      }
      createTitlePanelToolbar(myKey);
    }
    myMeanRowHeight = getList().getCellBounds(0, 0).height + UIUtil.getListCellVPadding() * 2;
  }

  private void createTitlePanelToolbar(@NotNull String dimensionKey) {
    myTitleToolbarPanel = new NonOpaquePanel();
    myTitleToolbarPanel.setLayout(new BoxLayout(myTitleToolbarPanel, BoxLayout.LINE_AXIS));
    myTitleToolbarPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    myRestoreSizeButton = new MyToolbarButton("Restore Size", CollapseComponent, CollapseComponentHover, e -> {
      WindowStateService.getInstance(myProject).putSizeFor(myProject, dimensionKey, null);
      myInternalSizeChanged = true;
      pack(true, true);
    }) {
      @Override
      protected boolean isButtonEnabled() {
        return myUserSizeChanged;
      }
    };

    mySettingsButton = new MyToolbarButton("Settings", AllIcons.General.Gear, AllIcons.General.GearHover, e -> {
      final ActionPopupMenu popupMenu =
        ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(BRANCH_POPUP, new DefaultActionGroup(mySettingsActions));
      popupMenu.getComponent().show(mySettingsButton, 0, assertNotNull(mySettingsButton).getHeight());
    }) {
      @Override
      protected boolean isButtonEnabled() {
        return !mySettingsActions.isEmpty();
      }
    };

    myTitleToolbarPanel.add(mySettingsButton);
    myTitleToolbarPanel.add(myRestoreSizeButton);
    getTitle().setButtonComponent(new ActiveComponent.Adapter() {
      @Override
      public JComponent getComponent() {
        return myTitleToolbarPanel;
      }
    }, JBUI.Borders.emptyRight(2));
  }

  //for child popups only
  private BranchActionGroupPopup(@Nullable WizardPopup aParent, @NotNull ListPopupStep aStep, @Nullable Object parentValue) {
    super(aParent, aStep, DataContext.EMPTY_CONTEXT, parentValue);
    // don't store children popup userSize;
    myKey = null;
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
  }

  private void trackDimensions(@Nullable String dimensionKey) {
    Window popupWindow = getPopupWindow();
    if (popupWindow == null) return;
    ComponentListener windowListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (myShown) {
          processOnSizeChanged();
        }
      }
    };
    popupWindow.addComponentListener(windowListener);
    addPopupListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        popupWindow.removeComponentListener(windowListener);
        if (dimensionKey != null && myUserSizeChanged) {
          WindowStateService.getInstance(myProject).putSizeFor(myProject, dimensionKey, myPrevSize);
        }
      }
    });
  }

  private void processOnSizeChanged() {
    Dimension newSize = assertNotNull(getSize());
    int preferredHeight = getComponent().getPreferredSize().height;
    int realHeight = getComponent().getHeight();
    boolean shouldExpand = preferredHeight + myMeanRowHeight < realHeight;
    boolean sizeWasIncreased = myPrevSize.height < newSize.height;
    if (!myInternalSizeChanged && sizeWasIncreased && shouldExpand) {
      List<MoreAction> mores = getMoreActions();
      for (MoreAction more : mores) {
        if (!getList().getScrollableTracksViewportHeight()) break;
        if (!more.isExpanded()) {
          more.setExpanded(true);
          getListModel().refilter();
        }
      }
    }
    myPrevSize = newSize;
    //ugly properties to distinguish user size changed from pack method call after Restore Size action performed
    myUserSizeChanged = !myInternalSizeChanged;
    myInternalSizeChanged = false;
    if (myRestoreSizeButton != null) {
      myRestoreSizeButton.update();
    }
  }

  @NotNull
  private List<MoreAction> getMoreActions() {
    List<MoreAction> result = ContainerUtil.newArrayList();
    ListPopupModel model = getListModel();
    for (int i = 0; i < model.getSize(); i++) {
      MoreAction moreAction = getSpecificAction(model.getElementAt(i), MoreAction.class);
      if (moreAction != null) {
        result.add(moreAction);
      }
    }
    return result;
  }

  public void addToolbarAction(@NotNull AnAction action, boolean underSettingsPopup) {
    if (myTitleToolbarPanel == null) return;
    if (mySettingsButton != null && underSettingsPopup) {
      mySettingsActions.add(action);
      mySettingsButton.update();
    }
    else {
      myTitleToolbarPanel.add(new MyToolbarButton(action) {
        @Override
        protected boolean isButtonEnabled() {
          return action.getTemplatePresentation().isEnabled();
        }
      });
    }
  }

  @NotNull
  private static ActionGroup createBranchSpeedSearchActionGroup(@NotNull ActionGroup actionGroup) {
    return new DefaultActionGroup(null, createSpeedSearchActions(actionGroup, true), false);
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  @Override
  protected void afterShow() {
    super.afterShow();
    myShown = true;
    Dimension size = getSize();
    if (size != null) {
      myPrevSize = size;
    }
    trackDimensions(myKey);
  }

  private static List<AnAction> createSpeedSearchActions(@NotNull ActionGroup parentActionGroup, boolean isFirstLevel) {
    // add per repository branches into the model as Speed Search elements and show them only if regular items were not found by mask;
    @NotNull List<AnAction> speedSearchActions = ContainerUtil.newArrayList();
    if (!isFirstLevel) speedSearchActions.add(new Separator(parentActionGroup.getTemplatePresentation().getText()));
    for (AnAction child : parentActionGroup.getChildren(null)) {
      if (child instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)child;
        if (isFirstLevel) {
          speedSearchActions.addAll(createSpeedSearchActions(childGroup, false));
        }
        else if (childGroup instanceof BranchActionGroup) {
          speedSearchActions.add(createSpeedSearchActionGroupWrapper(childGroup));
        }
        else if (childGroup instanceof HideableActionGroup) {
          speedSearchActions.add(createSpeedSearchActionGroupWrapper(((HideableActionGroup)childGroup).getDelegate()));
        }
      }
    }
    return speedSearchActions;
  }

  @Override
  public void handleSelect(boolean handleFinalChoices) {
    super.handleSelect(handleFinalChoices, null);
    if (getSpecificAction(getList().getSelectedValue(), MoreAction.class) != null) {
      getListModel().refilter();
    }
  }

  @Override
  public void handleSelect(boolean handleFinalChoices, InputEvent e) {
    BranchActionGroup branchActionGroup = getSelectedBranchGroup();
    if (branchActionGroup != null && e instanceof MouseEvent && myListElementRenderer.isIconAt(((MouseEvent)e).getPoint())) {
      branchActionGroup.toggle();
      getList().repaint();
    }
    else {
      super.handleSelect(handleFinalChoices, e);
    }
  }

  @Override
  protected void handleToggleAction() {
    BranchActionGroup branchActionGroup = getSelectedBranchGroup();
    if (branchActionGroup != null) {
      branchActionGroup.toggle();
      getList().repaint();
    }
    else {
      super.handleToggleAction();
    }
  }

  @Nullable
  private BranchActionGroup getSelectedBranchGroup() {
    return getSpecificAction(getList().getSelectedValue(), BranchActionGroup.class);
  }

  @Override
  protected void onSpeedSearchPatternChanged() {
    getList().setSelectedIndex(0);
    super.onSpeedSearchPatternChanged();
    ScrollingUtil.ensureSelectionExists(getList());
  }

  @Override
  protected boolean shouldUseStatistics() {
    return false;
  }

  protected boolean shouldBeShowing(@NotNull AnAction action) {
    if (!super.shouldBeShowing(action)) return false;
    if (getSpeedSearch().isHoldingFilter()) return !(action instanceof MoreAction);
    if (action instanceof MoreHideableActionGroup) return ((MoreHideableActionGroup)action).shouldBeShown();
    return true;
  }

  @Override
  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    return createListPopupStep(parent, step, parentValue);
  }

  private WizardPopup createListPopupStep(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new BranchActionGroupPopup(parent, (ListPopupStep)step, parentValue);
    }
    return super.createPopup(parent, step, parentValue);
  }

  @Override
  protected MyPopupListElementRenderer getListElementRenderer() {
    if (myListElementRenderer == null) {
      myListElementRenderer = new MyPopupListElementRenderer(this);
    }
    return myListElementRenderer;
  }

  private static class MyPopupListElementRenderer extends PopupListElementRendererWithIcon {
    private ErrorLabel myInfoLabel;

    public MyPopupListElementRenderer(ListPopupImpl aPopup) {
      super(aPopup);
    }

    @Override
    protected SeparatorWithText createSeparator() {
      return new MyTextSeparator();
    }

    @Override
    protected void customizeComponent(JList list, Object value, boolean isSelected) {
      MoreAction more = getSpecificAction(value, MoreAction.class);
      if (more != null) {
        myTextLabel.setForeground(JBColor.gray);
      }
      super.customizeComponent(list, value, isSelected);
      BranchActionGroup branchActionGroup = getSpecificAction(value, BranchActionGroup.class);
      if (branchActionGroup != null) {
        myTextLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        myTextLabel.setIcon(chooseUpdateIndicatorIcon(branchActionGroup));
      }
      PopupElementWithAdditionalInfo additionalInfoAction = getSpecificAction(value, PopupElementWithAdditionalInfo.class);
      updateInfoComponent(myInfoLabel, additionalInfoAction != null ? additionalInfoAction.getInfoText() : null, isSelected);
    }

    private static Icon chooseUpdateIndicatorIcon(@NotNull BranchActionGroup branchActionGroup) {
      if (branchActionGroup.hasIncomingCommits()) {
        return branchActionGroup.hasOutgoingCommits() ? IncomingOutgoing : Incoming;
      }
      return branchActionGroup.hasOutgoingCommits() ? Outgoing : null;
    }

    private void updateInfoComponent(@NotNull ErrorLabel infoLabel, @Nullable String infoText, boolean isSelected) {
      if (infoText != null) {
        infoLabel.setVisible(true);
        infoLabel.setText(infoText);

        if (isSelected) {
          setSelected(infoLabel);
        }
        else {
          infoLabel.setBackground(getBackground());
          infoLabel.setForeground(JBColor.GRAY);    // different foreground than for other elements
        }
      }
      else {
        infoLabel.setVisible(false);
      }
    }

    @Override
    protected JComponent createItemComponent() {
      myTextLabel = new ErrorLabel();
      myTextLabel.setOpaque(true);
      myTextLabel.setBorder(JBUI.Borders.empty(1));

      myInfoLabel = new ErrorLabel();
      myInfoLabel.setOpaque(true);
      myInfoLabel.setBorder(JBUI.Borders.empty(1, DEFAULT_HGAP, 1, 1));
      myInfoLabel.setFont(FontUtil.minusOne(myInfoLabel.getFont()));

      JPanel compoundPanel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
      myIconLabel = new IconComponent();
      myInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      JPanel compoundTextPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
      JPanel textPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
      compoundPanel.add(myIconLabel, BorderLayout.WEST);
      textPanel.add(myTextLabel, BorderLayout.WEST);
      textPanel.add(myInfoLabel, BorderLayout.CENTER);
      compoundTextPanel.add(textPanel, BorderLayout.CENTER);
      compoundPanel.add(compoundTextPanel, BorderLayout.CENTER);
      return layoutComponent(compoundPanel);
    }
  }

  private static class MyTextSeparator extends SeparatorWithText {

    public MyTextSeparator() {
      super();
      setTextForeground(JBColor.BLACK);
      setCaptionCentered(false);
      UIUtil.addInsets(this, DEFAULT_VGAP, UIUtil.getListCellHPadding(), 0, 0);
    }

    @Override
    protected void paintLine(Graphics g, int x, int y, int width) {
      if (StringUtil.isEmptyOrSpaces(getCaption())) {
        super.paintLine(g, x, y, width);
      }
    }
  }

  private static class MoreAction extends DumbAwareAction implements KeepingPopupOpenAction {

    @NotNull private final Project myProject;
    @Nullable private final String mySettingName;
    private final boolean myDefaultExpandValue;
    private boolean myIsExpanded;
    @NotNull private final String myToCollapseText;
    @NotNull private final String myToExpandText;

    public MoreAction(@NotNull Project project,
                      int numberOfHiddenNodes,
                      @Nullable String settingName,
                      boolean defaultExpandValue,
                      boolean hasFavorites) {
      super();
      myProject = project;
      mySettingName = settingName;
      myDefaultExpandValue = defaultExpandValue;
      assert numberOfHiddenNodes > 0;
      myToExpandText = "Show " + numberOfHiddenNodes + " More...";
      myToCollapseText = "Show " + (hasFavorites ? "Only Favorites" : "Less");
      setExpanded(
        settingName != null ? PropertiesComponent.getInstance(project).getBoolean(settingName, defaultExpandValue) : defaultExpandValue);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      setExpanded(!myIsExpanded);
      InputEvent event = e.getInputEvent();
      if (event != null && event.getSource() instanceof JComponent) {
        DataProvider dataProvider = DataManager.getDataProvider((JComponent)event.getSource());
        if (dataProvider != null) {
          assertNotNull(POPUP_MODEL.getData(dataProvider)).refilter();
        }
      }
    }

    public boolean isExpanded() {
      return myIsExpanded;
    }

    public void setExpanded(boolean isExpanded) {
      myIsExpanded = isExpanded;
      saveState();
      updateActionText();
    }

    private void updateActionText() {
      getTemplatePresentation().setText(myIsExpanded ? myToCollapseText : myToExpandText);
    }

    public void saveState() {
      if (mySettingName != null) {
        PropertiesComponent.getInstance(myProject).setValue(mySettingName, myIsExpanded, myDefaultExpandValue);
      }
    }
  }

  interface MoreHideableActionGroup {
    boolean shouldBeShown();
  }

  private static class HideableActionGroup extends EmptyAction.MyDelegatingActionGroup implements MoreHideableActionGroup, DumbAware {
    @NotNull private final MoreAction myMoreAction;

    private HideableActionGroup(@NotNull ActionGroup actionGroup, @NotNull MoreAction moreAction) {
      super(actionGroup);
      myMoreAction = moreAction;
    }

    @Override
    public boolean shouldBeShown() {
      return myMoreAction.isExpanded();
    }
  }

  public static void wrapWithMoreActionIfNeeded(@NotNull Project project,
                                                @NotNull DefaultActionGroup parentGroup, @NotNull List<? extends ActionGroup> actionList,
                                                int maxIndex, @Nullable String settingName) {
    wrapWithMoreActionIfNeeded(project, parentGroup, actionList, maxIndex, settingName, false);
  }

  public static void wrapWithMoreActionIfNeeded(@NotNull Project project,
                                                @NotNull DefaultActionGroup parentGroup, @NotNull List<? extends ActionGroup> actionList,
                                                int maxIndex, @Nullable String settingName, boolean defaultExpandValue) {
    if (actionList.size() > maxIndex) {
      boolean hasFavorites =
        actionList.stream().anyMatch(action -> action instanceof BranchActionGroup && ((BranchActionGroup)action).isFavorite());
      MoreAction moreAction = new MoreAction(project, actionList.size() - maxIndex, settingName, defaultExpandValue, hasFavorites);
      for (int i = 0; i < actionList.size(); i++) {
        parentGroup.add(i < maxIndex ? actionList.get(i) : new HideableActionGroup(actionList.get(i), moreAction));
      }
      parentGroup.add(moreAction);
    }
    else {
      parentGroup.addAll(actionList);
    }
  }

  private static abstract class MyToolbarButton extends JButton {

    private MyToolbarButton(@Nullable String text, @Nullable Icon icon, @Nullable Icon rolloverIcon) {
      setBorder(JBUI.Borders.empty(0, 2));
      setBorderPainted(false);
      setContentAreaFilled(false);
      setOpaque(false);
      setRolloverEnabled(true);
      Icon regularIcon = chooseNotNull(icon, EmptyIcon.ICON_0);
      setIcon(regularIcon);
      setToolTipText(text);
      setRolloverIcon(chooseNotNull(rolloverIcon, regularIcon));
      update();
      setUI(new BasicButtonUI());
    }

    public MyToolbarButton(@Nullable String text,
                           @Nullable Icon icon,
                           @Nullable Icon rolloverIcon,
                           @NotNull ActionListener buttonListener) {
      this(text, icon, rolloverIcon);
      addActionListener(buttonListener);
    }

    public MyToolbarButton(AnAction action) {
      this(action.getTemplatePresentation().getText(), action.getTemplatePresentation().getIcon(),
           action.getTemplatePresentation().getHoveredIcon());
      addActionListener(ActionUtil.createActionListener(action, this, BRANCH_POPUP));
    }

    public void update() {
      boolean enabled = isButtonEnabled();
      setEnabled(enabled);
      setVisible(enabled);
    }

    protected abstract boolean isButtonEnabled();
  }
}
