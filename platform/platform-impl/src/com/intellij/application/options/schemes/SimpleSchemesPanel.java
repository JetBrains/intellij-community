// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Basic implementation of {@link AbstractSchemesPanel} that provides simple informational label as right side of the panel.
 *
 * @see AbstractSchemeActions
 * @see SchemesModel
 */
public abstract class SimpleSchemesPanel<T extends Scheme> extends AbstractSchemesPanel<T, JLabel> {

  public SimpleSchemesPanel(int vGap) {
    super(vGap);
  }

  public SimpleSchemesPanel() {
    super();
  }

  @NotNull
  @Override
  protected JLabel createInfoComponent() {
    return new JLabel();
  }

  @Override
  public void showMessage(@Nullable @NlsContexts.Label String message, @NotNull MessageType messageType) {
    showMessage(message, messageType, myInfoComponent);
  }

  @Override
  public void clearMessage() {
    myInfoComponent.setText(null);
  }
}
