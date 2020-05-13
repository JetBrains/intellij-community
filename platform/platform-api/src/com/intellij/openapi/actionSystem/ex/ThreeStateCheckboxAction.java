// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class ThreeStateCheckboxAction extends AnAction implements CustomComponentAction {
  @NonNls public static final String STATE_PROPERTY = "three_state_selected";

  protected ThreeStateCheckboxAction() {}

  protected ThreeStateCheckboxAction(final @Nls(capitalization = Nls.Capitalization.Title) String text) {
    super(text);
  }

  protected ThreeStateCheckboxAction(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  protected ThreeStateCheckboxAction(final @Nls(capitalization = Nls.Capitalization.Title) String text,
                                     final @Nls(capitalization = Nls.Capitalization.Sentence) String description, final Icon icon) {
    super(text, description, icon);
  }

  protected ThreeStateCheckboxAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, final Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  @NotNull
  public abstract ThreeStateCheckBox.State isSelected(AnActionEvent e);

  public abstract void setSelected(AnActionEvent e, @NotNull ThreeStateCheckBox.State state);


  @Override
  public final void actionPerformed(@NotNull final AnActionEvent e) {
    ThreeStateCheckBox.State state = isSelected(e);

    Presentation presentation = e.getPresentation();
    presentation.putClientProperty(STATE_PROPERTY, state);

    setSelected(e, ThreeStateCheckBox.nextState(state, true));
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    ThreeStateCheckBox.State state = isSelected(e);

    Presentation presentation = e.getPresentation();
    presentation.putClientProperty(STATE_PROPERTY, state);

    JComponent property = presentation.getClientProperty(COMPONENT_KEY);
    if (property instanceof ThreeStateCheckBox) {
      ThreeStateCheckBox checkBox = (ThreeStateCheckBox)property;

      updateCustomComponent(checkBox, presentation);
    }
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    // this component cannot be stored right here because of action system architecture:
    // one action can be shown on multiple toolbars simultaneously
    ThreeStateCheckBox checkBox = new ThreeStateCheckBox();
    updateCustomComponent(checkBox, presentation);
    return CheckboxAction.createCheckboxComponent(checkBox, this, place);
  }

  protected void updateCustomComponent(ThreeStateCheckBox checkBox, Presentation presentation) {
    CheckboxAction.updateCheckboxPresentation(checkBox, presentation);

    ThreeStateCheckBox.State property = ObjectUtils.tryCast(presentation.getClientProperty(STATE_PROPERTY), ThreeStateCheckBox.State.class);
    checkBox.setState(ObjectUtils.chooseNotNull(property, ThreeStateCheckBox.State.NOT_SELECTED));
  }
}
