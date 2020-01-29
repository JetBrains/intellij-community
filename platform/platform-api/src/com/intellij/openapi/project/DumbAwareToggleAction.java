// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.project;

import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class DumbAwareToggleAction extends ToggleAction implements DumbAware {
  protected DumbAwareToggleAction() {
  }

  protected DumbAwareToggleAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String text) {
    super(text);
  }

  protected DumbAwareToggleAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String text,
                                  @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description, @Nullable Icon icon) {
    super(text, description, icon);
  }
}