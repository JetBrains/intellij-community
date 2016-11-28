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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

public class BranchActionGroupPopup extends FlatSpeedSearchPopup {
private static final String DIMENSION_SERVICE_KEY = "Vcs.Branch.Popup";

  public BranchActionGroupPopup(@NotNull String title, @NotNull Project project,
                                @NotNull Condition<AnAction> preselectActionCondition, @NotNull ActionGroup actions) {
    super(title, new DefaultActionGroup(actions, createBranchSpeedSearchActionGroup(actions)), SimpleDataContext.getProjectContext(project),
          preselectActionCondition, true);
    setDimensionServiceKey(DIMENSION_SERVICE_KEY);
  }

  //for child popups only
  private BranchActionGroupPopup(@Nullable WizardPopup aParent, @NotNull ListPopupStep aStep, @Nullable Object parentValue) {
    super(aParent, aStep, DataContext.EMPTY_CONTEXT, parentValue);
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
      }
    }
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
    }
    return null;
  }

  @Override
  protected ListCellRenderer getListElementRenderer() {
    return new MyPopupListElementRenderer(this);
  }

  private static class MyPopupListElementRenderer extends PopupListElementRenderer<Object> {

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
      super.customizeComponent(list, value, isSelected);

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
      compoundPanel.add(myTextLabel, BorderLayout.CENTER);
      compoundPanel.add(myInfoLabel, BorderLayout.EAST);

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
    }
  }
}
