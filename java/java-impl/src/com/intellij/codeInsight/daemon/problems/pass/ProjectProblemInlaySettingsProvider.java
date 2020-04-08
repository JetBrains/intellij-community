// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.hints.ImmediateConfigurable;
import com.intellij.codeInsight.hints.InlayHintsSettings;
import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.codeInsight.hints.SettingsKey;
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel;
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectProblemInlaySettingsProvider implements InlaySettingsProvider {

  @NotNull
  @Override
  public List<InlayProviderSettingsModel> createModels(@NotNull Project project, @NotNull Language language) {
    if (!Registry.is("project.problems.view")) return Collections.emptyList();
    InlayHintsSettings config = InlayHintsSettings.instance();
    boolean hintsEnabled = config.hintsEnabled(SettingsModel.SETTINGS_KEY, language);
    SettingsModel settingsModel = new SettingsModel(hintsEnabled, SettingsModel.SETTINGS_KEY.getId());
    return Collections.singletonList(settingsModel);
  }

  @NotNull
  @Override
  public Collection<Language> getSupportedLanguages(@NotNull Project project) {
    return Collections.singleton(JavaLanguage.INSTANCE);
  }

  static boolean hintsEnabled() {
    return InlayHintsSettings.instance().hintsEnabled(SettingsModel.SETTINGS_KEY, JavaLanguage.INSTANCE);
  }

  private static class SettingsModel extends InlayProviderSettingsModel {

    private static final SettingsKey<NoSettings> SETTINGS_KEY = new SettingsKey<>("java.project.problems");
    private final InlayHintsSettings config = InlayHintsSettings.instance();

    private SettingsModel(boolean isEnabled, @NotNull String id) {
      super(isEnabled, id);
    }

    @NotNull
    @Override
    public String getName() {
      return JavaErrorBundle.message("project.problems.title");
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JPanel();
    }

    @Override
    public void collectAndApply(@NotNull Editor editor, @NotNull PsiFile file) {
    }

    @Nullable
    @Override
    public String getPreviewText() {
      return null;
    }

    @Override
    public void apply() {
      config.changeHintTypeStatus(SETTINGS_KEY, JavaLanguage.INSTANCE, isEnabled());
    }

    @Override
    public boolean isModified() {
      return isEnabled() != config.hintsEnabled(SETTINGS_KEY, JavaLanguage.INSTANCE);
    }

    @Override
    public void reset() {
      setEnabled(config.hintsEnabled(SETTINGS_KEY, JavaLanguage.INSTANCE));
    }

    @NotNull
    @Override
    public String getMainCheckBoxLabel() {
      return "Show hints";
    }

    @NotNull
    @Override
    public List<ImmediateConfigurable.Case> getCases() {
      return Collections.emptyList();
    }
  }
}
