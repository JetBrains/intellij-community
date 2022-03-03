// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.ide.ui.AppearanceOptionsTopHitProviderKt;
import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class FileColorsOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @Override
  public @NotNull String getId() {
    return AppearanceOptionsTopHitProviderKt.APPEARANCE_ID;
  }

  @Override
  public @NotNull Collection<OptionDescription> getOptions(@NotNull Project project) {
    BooleanOptionDescription enabled = new PublicMethodBasedOptionDescription(LangBundle.message("label.file.colors.enabled"),
                                                                              "reference.settings.ide.settings.file-colors",
                                                                              "isEnabled", "setEnabled",
                                                                              () -> FileColorManager.getInstance(project)) {
      @Override
      protected void fireUpdated() {
        UISettings.getInstance().fireUISettingsChanged();
      }
    };
    if (!enabled.isOptionEnabled()) {
      return Collections.singletonList(enabled);
    }

    return List.of(
      enabled,
      new BooleanOptionDescription(LangBundle.message("label.use.file.colors.in.editor.tabs"), "reference.settings.ide.settings.file-colors") {
        @Override
        public boolean isOptionEnabled() {
          return FileColorManagerImpl._isEnabledForTabs();
        }

        @Override
        public void setOptionState(boolean value) {
          FileColorManagerImpl.setEnabledForTabs(value);
        }
      },
      new BooleanOptionDescription(LangBundle.message("label.use.file.colors.in.project.view"), "reference.settings.ide.settings.file-colors") {
        @Override
        public boolean isOptionEnabled() {
          return FileColorManagerImpl._isEnabledForProjectView();
        }

        @Override
        public void setOptionState(boolean value) {
          FileColorManagerImpl.setEnabledForProjectView(value);
        }
      }
    );
  }
}
