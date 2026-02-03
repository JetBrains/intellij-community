// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ArrangementLabelUiComponent extends AbstractArrangementUiComponent {

  private final @NotNull ArrangementAtomMatchCondition myCondition;
  private final @NotNull JLabel                        myLabel;

  public ArrangementLabelUiComponent(@NotNull ArrangementSettingsToken token) {
    super(token);
    myCondition = new ArrangementAtomMatchCondition(token);
    myLabel = new JLabel(token.getRepresentationValue());
  }

  @Override
  public @NotNull ArrangementSettingsToken getToken() {
    return myCondition.getType();
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull ArrangementMatchCondition getMatchCondition() {
    return myCondition;
  }

  @Override
  protected JComponent doGetUiComponent() {
    return myLabel;
  }

  @Override
  public boolean isSelected() {
    return true;
  }

  @Override
  public void setSelected(boolean selected) {
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
  }

  @Override
  protected void doReset() {
  }
  
  @Override
  public int getBaselineToUse(int width, int height) {
    return myLabel.getBaseline(width, height);
  }

  @Override
  public void handleMouseClickOnSelected() {
    setSelected(false);
  }
}
