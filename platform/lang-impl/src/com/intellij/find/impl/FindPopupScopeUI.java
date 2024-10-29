// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public interface FindPopupScopeUI {
  Pair<ScopeType, JComponent> @NotNull [] getComponents();

  @NotNull
  ScopeType initByModel(@NotNull FindModel findModel);
  void applyTo(@NotNull FindSettings findSettings, @NotNull FindPopupScopeUI.ScopeType selectedScope);
  void applyTo(@NotNull FindModel findModel, @NotNull FindPopupScopeUI.ScopeType selectedScope);

  default @Nullable("null means OK") ValidationInfo validate(@NotNull FindModel model, FindPopupScopeUI.ScopeType selectedScope) {
    return null;
  }

  /**
   * @return true if something was hidden
   */
  boolean hideAllPopups();

  final class ScopeType {
    public final String name;
    public Supplier<@NlsContexts.ListItem String> textComputable;
    public final Icon icon;

    public ScopeType(String name, Supplier<@NlsContexts.ListItem String> textComputable, Icon icon) {
      this.name = name;
      this.textComputable = textComputable;
      this.icon = icon;
    }
  }
}
