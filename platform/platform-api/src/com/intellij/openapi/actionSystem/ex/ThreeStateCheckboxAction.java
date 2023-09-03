// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class ThreeStateCheckboxAction extends AnAction implements CustomComponentAction {
  public static final @NonNls Key<ThreeStateCheckBox.State> STATE_PROPERTY = Key.create("three_state_selected");

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

  public abstract @NotNull ThreeStateCheckBox.State isSelected(AnActionEvent e);

  public abstract void setSelected(AnActionEvent e, @NotNull ThreeStateCheckBox.State state);


  @Override
  public final void actionPerformed(final @NotNull AnActionEvent e) {
    ThreeStateCheckBox.State state = isSelected(e);

    Presentation presentation = e.getPresentation();
    presentation.putClientProperty(STATE_PROPERTY, state);

    setSelected(e, ThreeStateCheckBox.nextState(state, true));
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    ThreeStateCheckBox.State state = isSelected(e);

    Presentation presentation = e.getPresentation();
    presentation.putClientProperty(STATE_PROPERTY, state);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    // this component cannot be stored right here because of the action system architecture:
    // one action can be shown on multiple toolbars simultaneously
    ThreeStateCheckBox checkBox = new ThreeStateCheckBox();
    return CheckboxAction.createCheckboxComponent(checkBox, this, place);
  }

  @Override
  public void updateCustomComponent(@NotNull JComponent component,
                                    @NotNull Presentation presentation) {
    if (component instanceof ThreeStateCheckBox checkBox) {
      CheckboxAction.updateCheckboxPresentation(checkBox, presentation);

      ThreeStateCheckBox.State property = presentation.getClientProperty(STATE_PROPERTY);
      checkBox.setState(ObjectUtils.chooseNotNull(property, ThreeStateCheckBox.State.NOT_SELECTED));
    }
  }
}
