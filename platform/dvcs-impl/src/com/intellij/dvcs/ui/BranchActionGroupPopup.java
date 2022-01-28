// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.util.FontUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.icons.AllIcons.General.FitContent;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;

public final class BranchActionGroupPopup extends FlatSpeedSearchPopup {
  private static final DataKey<ListPopupModel> POPUP_MODEL = DataKey.create("VcsPopupModel");

  private static final String EXPERIMENTAL_UI_DIMENSION_KEY_SUFFIX = ".ExperimentalUi";

  static final String BRANCH_POPUP = "BranchWidget";
  private static final int BRANCH_POPUP_ROW_COUNT = 30;
  private Project myProject;
  private MyPopupListElementRenderer myListElementRenderer;
  private boolean myShown;
  private boolean myUserSizeChanged;
  private boolean myInternalSizeChanged;
  private int myMeanRowHeight;
  @Nullable private final String myKey;
  @NotNull private Dimension myPrevSize = JBUI.emptySize();

  private final List<AnAction> mySettingsActions = new ArrayList<>();
  private final List<AnAction> myToolbarActions = new ArrayList<>();

  public BranchActionGroupPopup(@Nullable @NlsContexts.PopupTitle String title,
                                @NotNull Project project,
                                @NotNull Condition<? super AnAction> preselectActionCondition,
                                @NotNull ActionGroup actions,
                                @Nullable String dimensionKey,
                                @NotNull DataContext dataContext) {
    super(title, ActionGroupUtil.forceRecursiveUpdateInBackground(createBranchSpeedSearchActionGroup(actions)), dataContext, preselectActionCondition, true);
    getTitle().setBackground(JBColor.PanelBackground);
    myProject = project;
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    myKey = buildDimensionKey(dimensionKey);
    if (myKey != null) {
      setDimensionServiceKey(myKey);
      if (WindowStateService.getInstance(myProject).getSizeFor(myProject, myKey) != null) {
        myUserSizeChanged = true;
      }
      createTitlePanelToolbar(myKey);
    }
    setSpeedSearchAlwaysShown();
    myMeanRowHeight = getList().getCellBounds(0, 0).height + UIUtil.getListCellVPadding() * 2;
    setMaxRowCount(BRANCH_POPUP_ROW_COUNT);
    getList().setVisibleRowCount(BRANCH_POPUP_ROW_COUNT);
  }

  @Nullable
  private static String buildDimensionKey(@Nullable final String initialDimensionKey) {
    if (initialDimensionKey == null) return null;
    return ExperimentalUI.isNewVcsBranchPopup() ? initialDimensionKey + EXPERIMENTAL_UI_DIMENSION_KEY_SUFFIX : initialDimensionKey;
  }

  private void createTitlePanelToolbar(@NotNull String dimensionKey) {
    ActionGroup actionGroup = new LightActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return myToolbarActions.toArray(AnAction.EMPTY_ARRAY);
      }
    };
    AnAction restoreSizeButton =
      new AnAction(DvcsBundle.messagePointer("action.BranchActionGroupPopup.Anonymous.text.restore.size"), FitContent) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        WindowStateService.getInstance(myProject).putSizeFor(myProject, dimensionKey, null);
        myInternalSizeChanged = true;
        pack(true, true);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myUserSizeChanged);
      }
    };
    ActionGroup settingsGroup = new ActionGroup(DvcsBundle.message("action.BranchActionGroupPopup.settings.text"), true) {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return mySettingsActions.toArray(AnAction.EMPTY_ARRAY);
      }

      @Override
      public boolean hideIfNoVisibleChildren() {
        return true;
      }
    };
    settingsGroup.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);

    myToolbarActions.add(restoreSizeButton);
    myToolbarActions.add(settingsGroup);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(BRANCH_POPUP, actionGroup, true);
    toolbar.setTargetComponent(getList());
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setOpaque(false);
    getTitle().setButtonComponent(new ActiveComponent.Adapter() {
      @NotNull
      @Override
      public JComponent getComponent() {
        return toolbar.getComponent();
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
    addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        popupWindow.removeComponentListener(windowListener);
      }
    });
  }

  @Override
  public void storeDimensionSize() {
    if (myKey != null && myUserSizeChanged) {
      super.storeDimensionSize();
    }
  }

  private void processOnSizeChanged() {
    Dimension newSize = Objects.requireNonNull(getSize());
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
  }

  @NotNull
  private List<MoreAction> getMoreActions() {
    List<MoreAction> result = new ArrayList<>();
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
    if (underSettingsPopup) {
      mySettingsActions.add(action);
    }
    else {
      myToolbarActions.add(0, action);
    }
  }

  public void update() {
    getContent().revalidate();
    getContent().repaint();
  }

  @NotNull
  private static ActionGroup createBranchSpeedSearchActionGroup(@NotNull ActionGroup actions) {
    LightActionGroup group = new LightActionGroup();
    group.add(actions);
    group.addAll(createSpeedSearchActions(actions, true));
    return group;
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
    if (parentActionGroup instanceof HideableActionGroup) {
      parentActionGroup = ((HideableActionGroup)parentActionGroup).getDelegate();
    }

    if (parentActionGroup instanceof BranchActionGroup) return Collections.emptyList();

    // add per repository branches into the model as Speed Search elements and show them only if regular items were not found by mask;
    List<AnAction> speedSearchActions = new ArrayList<>();
    if (!isFirstLevel) speedSearchActions.add(new Separator(parentActionGroup.getTemplatePresentation().getText()));
    for (AnAction child : parentActionGroup.getChildren(null)) {
      if (child instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)child;
        if (childGroup instanceof HideableActionGroup) {
          childGroup = ((HideableActionGroup)childGroup).getDelegate();
        }

        if (isFirstLevel) {
          speedSearchActions.addAll(createSpeedSearchActions(childGroup, false));
        }
        else if (childGroup instanceof BranchActionGroup) {
          speedSearchActions.add(createSpeedSearchActionGroupWrapper(childGroup));
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
    String newFilter = mySpeedSearch.getFilter();
    if (newFilter.endsWith(" ")) {
      mySpeedSearch.updatePattern(newFilter.trim());
      return;
    }
    getList().setSelectedIndex(0);
    super.onSpeedSearchPatternChanged();
    ScrollingUtil.ensureSelectionExists(getList());
  }

  @Override
  protected boolean shouldUseStatistics() {
    return false;
  }

  @Override
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

    MyPopupListElementRenderer(ListPopupImpl aPopup) {
      super(aPopup);
    }

    @Override
    protected void customizeComponent(JList list, Object value, boolean isSelected) {
      MoreAction more = getSpecificAction(value, MoreAction.class);
      if (more != null) {
        myTextLabel.setForeground(JBColor.gray);
      }
      super.customizeComponent(list, value, isSelected);
      if (mySeparatorComponent.isVisible()) {
        boolean hideLineAboveCaption = !ExperimentalUI.isNewVcsBranchPopup() && StringUtil.isNotEmpty(mySeparatorComponent.getCaption());
        ((GroupHeaderSeparator)mySeparatorComponent).setHideLine(myCurrentIndex == 0 || hideLineAboveCaption);
      }

      CustomIconProvider actionWithIconProvider = getSpecificAction(value, CustomIconProvider.class);
      myTextLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
      myTextLabel.setIcon(actionWithIconProvider != null ? actionWithIconProvider.getRightIcon() : null);
      PopupElementWithAdditionalInfo additionalInfoAction = getSpecificAction(value, PopupElementWithAdditionalInfo.class);
      updateInfoComponent(myInfoLabel, additionalInfoAction != null ? additionalInfoAction.getInfoText() : null, isSelected);
    }

    private void updateInfoComponent(@NotNull ErrorLabel infoLabel, @Nullable @Nls String infoText, boolean isSelected) {
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

  private static class MoreAction extends DumbAwareAction implements KeepingPopupOpenAction {

    @NotNull private final Project myProject;
    @Nullable private final @NonNls String mySettingName;
    private final boolean myDefaultExpandValue;
    private boolean myIsExpanded;
    @NotNull private final @Nls String myToCollapseText;
    @NotNull private final @Nls String myToExpandText;

    MoreAction(@NotNull Project project,
               int numberOfHiddenNodes,
               @Nullable @NonNls String settingName,
               boolean defaultExpandValue,
               boolean hasFavorites) {
      super();
      myProject = project;
      mySettingName = settingName;
      myDefaultExpandValue = defaultExpandValue;
      assert numberOfHiddenNodes > 0;
      myToExpandText = DvcsBundle.message("action.branch.popup.show.n.nodes.more", numberOfHiddenNodes);
      myToCollapseText = hasFavorites ? DvcsBundle.message("action.branch.popup.show.only.favorites")
                                      : DvcsBundle.message("action.branch.popup.show.less");
      setExpanded(
        settingName != null ? PropertiesComponent.getInstance(project).getBoolean(settingName, defaultExpandValue) : defaultExpandValue);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setExpanded(!myIsExpanded);
      InputEvent event = e.getInputEvent();
      if (event != null && event.getSource() instanceof JComponent) {
        DataProvider dataProvider = DataManager.getDataProvider((JComponent)event.getSource());
        if (dataProvider != null) {
          Objects.requireNonNull(POPUP_MODEL.getData(dataProvider)).refilter();
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

  private static final class HideableActionGroup extends EmptyAction.MyDelegatingActionGroup implements MoreHideableActionGroup, DumbAware, AlwaysVisibleActionGroup {
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
                                                @NotNull LightActionGroup parentGroup, @NotNull List<? extends ActionGroup> actionList,
                                                int maxIndex, @Nullable String settingName) {
    wrapWithMoreActionIfNeeded(project, parentGroup, actionList, maxIndex, settingName, false);
  }

  public static void wrapWithMoreActionIfNeeded(@NotNull Project project,
                                                @NotNull LightActionGroup parentGroup, @NotNull List<? extends ActionGroup> actionList,
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
}
