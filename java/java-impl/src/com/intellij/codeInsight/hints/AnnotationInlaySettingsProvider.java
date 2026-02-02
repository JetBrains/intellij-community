// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.hints.declarative.InlayHintsCustomSettingsProvider;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * @author Bas Leijdekkers
 */
final class AnnotationInlaySettingsProvider implements InlayHintsCustomSettingsProvider<Boolean> {
  
  private boolean myShortenNotNull;

  AnnotationInlaySettingsProvider() {
    myShortenNotNull = AnnotationInlaySettings.getInstance().shortenNotNull;
  }

  @Override
  public @NotNull JComponent createComponent(@NotNull Project project, @NotNull Language language) {
    final JBCheckBox myCheckBox = new JBCheckBox(JavaBundle.message("settings.inlay.java.shortened.notnull.annotation"), myShortenNotNull);
    myCheckBox.addItemListener(e -> myShortenNotNull = myCheckBox.isSelected());
    return myCheckBox;
  }

  @Override
  public boolean isDifferentFrom(@NotNull Project project, Boolean shortenNotNull) {
    return myShortenNotNull != shortenNotNull.booleanValue();
  }

  @Override
  public Boolean getSettingsCopy() {
    return myShortenNotNull;
  }

  @Override
  public void persistSettings(@NotNull Project project, Boolean shortenNotNull, @NotNull Language language) {
    AnnotationInlaySettings.getInstance().shortenNotNull = shortenNotNull.booleanValue();
  }

  @Override
  public void putSettings(@NotNull Project project, Boolean settings, @NotNull Language language) {}
}
