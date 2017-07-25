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
package com.intellij.dvcs.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.IconListPopupRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.FontUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

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
  private MyToolbarButton myRestoreSizeButton;
  private MyToolbarButton mySettingsButton;
  private final List<AnAction> mySettingsActions = ContainerUtil.newArrayList();

  public BranchActionGroupPopup(@NotNull String title,
                                @NotNull Project project,
                                @NotNull Condition<AnAction> preselectActionCondition,
                                @NotNull ActionGroup actions,
                                @Nullable String dimensionKey) {
    super(title, new DefaultActionGroup(actions, createBranchSpeedSearchActionGroup(actions)), SimpleDataContext.getProjectContext(project),
          preselectActionCondition, true);
    myProject = project;
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    installOnHoverIconsSupport(getListElementRenderer());
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
    JPanel panel = new NonOpaquePanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
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
      popupMenu.getComponent().show(mySettingsButton, 0, mySettingsButton.getHeight());
    }) {
      @Override
      protected boolean isButtonEnabled() {
        return !mySettingsActions.isEmpty();
      }
    };
    mySettingsButton.setBorder(JBUI.Borders.emptyLeft(4));

    panel.add(myRestoreSizeButton);
    panel.add(mySettingsButton);
    getTitle().setButtonComponent(new ActiveComponent.Adapter() {
      @Override
      public JComponent getComponent() {
        return panel;
      }
    }, JBUI.Borders.emptyRight(2));
  }

  //for child popups only
  private BranchActionGroupPopup(@Nullable WizardPopup aParent, @NotNull ListPopupStep aStep, @Nullable Object parentValue) {
    super(aParent, aStep, DataContext.EMPTY_CONTEXT, parentValue);
    // don't store children popup userSize;
    myKey = null;
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    installOnHoverIconsSupport(getListElementRenderer());
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
    Dimension newSize = ObjectUtils.assertNotNull(getSize());
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
    myRestoreSizeButton.update();
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

  public void addSettingAction(@NotNull AnAction action) {
    mySettingsActions.add(action);
    mySettingsButton.update();
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

  private static <T> T getSpecificAction(Object value, @NotNull Class<T> clazz) {
    if (value instanceof PopupFactoryImpl.ActionItem) {
      AnAction action = ((PopupFactoryImpl.ActionItem)value).getAction();
      if (clazz.isInstance(action)) {
        return clazz.cast(action);
      }
      else if (action instanceof EmptyAction.MyDelegatingActionGroup) {
        ActionGroup group = ((EmptyAction.MyDelegatingActionGroup)action).getDelegate();
        return clazz.isInstance(group) ? clazz.cast(group) : null;
      }
    }
    return null;
  }

  @Override
  protected MyPopupListElementRenderer getListElementRenderer() {
    if (myListElementRenderer == null) {
      myListElementRenderer = new MyPopupListElementRenderer(this);
    }
    return myListElementRenderer;
  }

  private class MyPopupListElementRenderer extends PopupListElementRenderer<Object> implements IconListPopupRenderer {

    private ErrorLabel myPrefixLabel;
    private ErrorLabel myInfoLabel;
    private IconComponent myIconLabel;

    public MyPopupListElementRenderer(ListPopupImpl aPopup) {
      super(aPopup);
    }

    @Override
    protected SeparatorWithText createSeparator() {
      return new MyTextSeparator();
    }

    @Override
    public boolean isIconAt(@NotNull Point point) {
      JList list = getList();
      int index = getList().locationToIndex(point);
      Rectangle bounds = getList().getCellBounds(index, index);
      Component renderer = getListCellRendererComponent(list, list.getSelectedValue(), index, true, true);
      renderer.setBounds(bounds);
      renderer.doLayout();
      point.translate(-bounds.x, -bounds.y);
      return SwingUtilities.getDeepestComponentAt(renderer, point.x, point.y) instanceof IconComponent;
    }

    @Override
    protected void customizeComponent(JList list, Object value, boolean isSelected) {
      MoreAction more = getSpecificAction(value, MoreAction.class);
      if (more != null) {
        myTextLabel.setForeground(JBColor.gray);
      }
      super.customizeComponent(list, value, isSelected);
      myTextLabel.setIcon(null);
      myTextLabel.setDisabledIcon(null);
      if (value instanceof PopupFactoryImpl.ActionItem) {
        ((PopupFactoryImpl.ActionItem)value).setIconHovered(isSelected);
      }
      myIconLabel.setIcon(myDescriptor.getIconFor(value));
      PopupElementWithAdditionalInfo additionalInfoAction = getSpecificAction(value, PopupElementWithAdditionalInfo.class);
      updateInfoComponent(myPrefixLabel, additionalInfoAction != null ? additionalInfoAction.getPrefixInfo() : null, isSelected);
      updateInfoComponent(myInfoLabel, additionalInfoAction != null ? additionalInfoAction.getInfoText() : null, isSelected);
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
      myPrefixLabel = new ErrorLabel();
      myPrefixLabel.setOpaque(true);
      myPrefixLabel.setBorder(JBUI.Borders.empty(1, 1, 1, DEFAULT_HGAP));
      Font minusOneFont = FontUtil.minusOne(myPrefixLabel.getFont());
      myPrefixLabel.setFont(minusOneFont);

      myTextLabel = new ErrorLabel();
      myTextLabel.setOpaque(true);
      myTextLabel.setBorder(JBUI.Borders.empty(1));

      myInfoLabel = new ErrorLabel();
      myInfoLabel.setOpaque(true);
      myInfoLabel.setBorder(JBUI.Borders.empty(1, DEFAULT_HGAP, 1, 1));
      myInfoLabel.setFont(minusOneFont);

      JPanel compoundPanel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
      myIconLabel = new IconComponent();
      myInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      JPanel compoundTextPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
      JPanel textPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
      compoundPanel.add(myIconLabel, BorderLayout.WEST);
      textPanel.add(myTextLabel, BorderLayout.WEST);
      textPanel.add(myInfoLabel, BorderLayout.CENTER);
      compoundTextPanel.add(myPrefixLabel, BorderLayout.WEST);
      compoundTextPanel.add(textPanel, BorderLayout.CENTER);
      compoundPanel.add(compoundTextPanel, BorderLayout.CENTER);
      return layoutComponent(compoundPanel);
    }

    private class IconComponent extends JLabel {
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
          ObjectUtils.assertNotNull(POPUP_MODEL.getData(dataProvider)).refilter();
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

  private static class HideableActionGroup extends EmptyAction.MyDelegatingActionGroup implements MoreHideableActionGroup {
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
    public MyToolbarButton(@NotNull String text, @NotNull Icon icon, @NotNull Icon rolloverIcon, @NotNull ActionListener buttonListener) {
      super(icon);
      setToolTipText(text);
      setBorder(IdeBorderFactory.createEmptyBorder());
      setBorderPainted(false);
      setContentAreaFilled(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setRolloverIcon(rolloverIcon);
      addActionListener(buttonListener);
      update();
      setUI(new BasicButtonUI());
    }

    public void update() {
      boolean enabled = isButtonEnabled();
      setEnabled(enabled);
      setVisible(enabled);
    }

    protected abstract boolean isButtonEnabled();
  }
}
