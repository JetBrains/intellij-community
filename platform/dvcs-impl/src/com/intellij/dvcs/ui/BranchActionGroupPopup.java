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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.IconListPopupRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

public class BranchActionGroupPopup extends FlatSpeedSearchPopup {
  private static final DataKey<ListPopupModel> POPUP_MODEL = DataKey.create("VcsPopupModel");
  private MyPopupListElementRenderer myListElementRenderer;

  public BranchActionGroupPopup(@NotNull String title, @NotNull Project project,
                                @NotNull Condition<AnAction> preselectActionCondition, @NotNull ActionGroup actions) {
    super(title, new DefaultActionGroup(actions, createBranchSpeedSearchActionGroup(actions)), SimpleDataContext.getProjectContext(project),
          preselectActionCondition, true);
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    installOnHoverIconsSupport(getListElementRenderer());
  }

  //for child popups only
  private BranchActionGroupPopup(@Nullable WizardPopup aParent, @NotNull ListPopupStep aStep, @Nullable Object parentValue) {
    super(aParent, aStep, DataContext.EMPTY_CONTEXT, parentValue);
    DataManager.registerDataProvider(getList(), dataId -> POPUP_MODEL.is(dataId) ? getListModel() : null);
    installOnHoverIconsSupport(getListElementRenderer());
  }

  @NotNull
  public static ActionGroup createBranchSpeedSearchActionGroup(@NotNull ActionGroup actionGroup) {
    DefaultActionGroup speedSearchActions = new DefaultActionGroup();
    createSpeedSearchActions(actionGroup, speedSearchActions, true);
    return speedSearchActions;
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  private static void createSpeedSearchActions(@NotNull ActionGroup actionGroup,
                                               @NotNull DefaultActionGroup speedSearchActions,
                                               boolean isFirstLevel) {
    // add per repository branches into the model as Speed Search elements and show them only if regular items were not found by mask;
    if (!isFirstLevel) speedSearchActions.addSeparator(actionGroup.getTemplatePresentation().getText());
    for (AnAction child : actionGroup.getChildren(null)) {
      if (child instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)child;
        if (isFirstLevel) {
          createSpeedSearchActions(childGroup, speedSearchActions, false);
        }
        else if (childGroup instanceof BranchActionGroup) {
          speedSearchActions.add(createSpeedSearchActionGroupWrapper(childGroup));
        }
        else if (childGroup instanceof HideableActionGroup) {
          speedSearchActions.add(createSpeedSearchActionGroupWrapper(((HideableActionGroup)childGroup).getDelegate()));
        }
      }
    }
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
    if (action instanceof MoreAction) return !((MoreAction)action).myIsExpanded;
    if (action instanceof MoreHideableActionGroup) return ((MoreHideableActionGroup)action).shouldBeShown();
    return true;
  }

  @Override
  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    WizardPopup popup = createListPopupStep(parent, step, parentValue);
    RootAction rootAction = getRootAction(parentValue);
    if (rootAction != null) {
      popup.setAdText((rootAction).getCaption());
    }
    return popup;
  }

  private WizardPopup createListPopupStep(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new BranchActionGroupPopup(parent, (ListPopupStep)step, parentValue);
    }
    return super.createPopup(parent, step, parentValue);
  }

  @Nullable
  private static RootAction getRootAction(Object value) {
    return getSpecificAction(value, RootAction.class);
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
      myIconLabel.setIcon(myDescriptor.getIconFor(value));
      PopupElementWithAdditionalInfo additionalInfoAction = getSpecificAction(value, PopupElementWithAdditionalInfo.class);
      String infoText = additionalInfoAction != null ? additionalInfoAction.getInfoText() : null;
      if (infoText != null) {
        myInfoLabel.setVisible(true);
        myInfoLabel.setText(infoText);

        if (isSelected) {
          setSelected(myInfoLabel);
        }
        else {
          myInfoLabel.setBackground(getBackground());
          myInfoLabel.setForeground(JBColor.GRAY);    // different foreground than for other elements
        }
      }
      else {
        myInfoLabel.setVisible(false);
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

      JPanel compoundPanel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
      myIconLabel = new IconComponent();
      myInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      JPanel textPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
      compoundPanel.add(myIconLabel, BorderLayout.WEST);
      textPanel.add(myTextLabel, BorderLayout.WEST);
      textPanel.add(myInfoLabel, BorderLayout.CENTER);
      compoundPanel.add(textPanel, BorderLayout.CENTER);
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
    private boolean myIsExpanded = false;

    public MoreAction(int numberOfHiddenNodes) {
      super();
      assert numberOfHiddenNodes > 0;
      getTemplatePresentation().setText(numberOfHiddenNodes + " more...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myIsExpanded = true;
      InputEvent event = e.getInputEvent();
      if (event != null && event.getSource() instanceof JComponent) {
        DataProvider dataProvider = DataManager.getDataProvider((JComponent)event.getSource());
        if (dataProvider != null) {
          ObjectUtils.assertNotNull(POPUP_MODEL.getData(dataProvider)).refilter();
        }
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
      return myMoreAction.myIsExpanded;
    }
  }

  public static void wrapWithMoreActionIfNeeded(@NotNull DefaultActionGroup parentGroup,
                                                @NotNull List<? extends ActionGroup> actionList,
                                                int maxIndex) {
    if (actionList.size() > maxIndex) {
      MoreAction moreAction = new MoreAction(actionList.size() - maxIndex);
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
