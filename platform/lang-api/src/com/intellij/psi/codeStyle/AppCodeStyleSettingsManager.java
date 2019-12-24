// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@State(name = "CodeStyleSettingsManager", storages = @Storage("code.style.schemes"))
public final class AppCodeStyleSettingsManager extends CodeStyleSettingsManager {

  public AppCodeStyleSettingsManager() {
    registerExtensionPointListeners(ApplicationManager.getApplication());
  }

  @Override
  protected void registerExtensionPointListeners(@NotNull Disposable disposable) {
    super.registerExtensionPointListeners(disposable);
    LanguageCodeStyleSettingsProvider.EP_NAME.addExtensionPointListener(
      new ExtensionPointListener<LanguageCodeStyleSettingsProvider>() {
        @Override
        public void extensionAdded(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
          LanguageCodeStyleSettingsProvider.resetSettingsPagesProviders();
        }

        @Override
        public void extensionRemoved(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
          LanguageCodeStyleSettingsProvider.resetSettingsPagesProviders();
        }
      },
      disposable
    );
  }

  @Override
  protected Collection<CodeStyleSettings> enumSettings() {
    return getMainProjectCodeStyle() != null ?
           Collections.singletonList(getMainProjectCodeStyle()) : Collections.emptyList();
  }
}
