/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.MessageType;
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

  public SimpleSchemesPanel(int vGap, @Nullable JComponent rightCustomComponent) {
    super(vGap, rightCustomComponent);
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
  public void showMessage(@Nullable String message, @NotNull MessageType messageType) {
    showMessage(message, messageType, myInfoComponent);
  }

  @Override
  public void clearMessage() {
    myInfoComponent.setText(null);
  }
}
