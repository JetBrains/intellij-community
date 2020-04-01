/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
public abstract class DumbAwareActionButton extends AnActionButton implements DumbAware {

  public DumbAwareActionButton(@ActionText String text) {
    super(text);
  }

  public DumbAwareActionButton(@ActionText String text,
                               @NlsActions.ActionDescription String description,
                               @Nullable Icon icon) {
    super(text, description, icon);
  }

  public DumbAwareActionButton(@NotNull Supplier<String> dynamicText,
                               @NotNull Supplier<String> dynamicDescription,
                               @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  public DumbAwareActionButton(@ActionText String text,
                               Icon icon) {
    super(text, icon);
  }

  public DumbAwareActionButton(@NotNull Supplier<String> dynamicText, Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  public DumbAwareActionButton() {
  }
}
