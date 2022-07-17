// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CodeStyleSettingsServiceImpl implements CodeStyleSettingsService {
  @Override
  public void addListener(@NotNull CodeStyleSettingsServiceListener listener, @Nullable Disposable disposable) {
    FileTypeIndentOptionsProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FileTypeIndentOptionsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        listener.fileTypeIndentOptionsFactoryAdded(extension);
      }

      @Override
      public void extensionRemoved(@NotNull FileTypeIndentOptionsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        listener.fileTypeIndentOptionsFactoryRemoved(extension);
      }
    }, disposable);
    LanguageCodeStyleSettingsProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        LanguageCodeStyleSettingsProvider.registerSettingsPageProvider(extension);
        listener.languageCodeStyleProviderAdded(extension);
      }

      @Override
      public void extensionRemoved(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        LanguageCodeStyleSettingsProvider.unregisterSettingsPageProvider(extension);
        listener.languageCodeStyleProviderRemoved(extension);
      }
    }, disposable);
    CodeStyleSettingsProvider.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull CodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        listener.customCodeStyleSettingsFactoryAdded(extension);
      }

      @Override
      public void extensionRemoved(@NotNull CodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        listener.customCodeStyleSettingsFactoryRemoved(extension);
      }
    }, disposable);
  }

  @Override
  public @NotNull List<? extends FileTypeIndentOptionsFactory> getFileTypeIndentOptionsFactories() {
    return FileTypeIndentOptionsProvider.EP_NAME.getExtensionList();
  }

  @Override
  public @NotNull List<? extends CustomCodeStyleSettingsFactory> getCustomCodeStyleSettingsFactories() {
    List<CustomCodeStyleSettingsFactory> result = new ArrayList<>();
    result.addAll(CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList());
    result.addAll(LanguageCodeStyleSettingsProvider.getSettingsPagesProviders());
    return result;
  }

  @Override
  public @NotNull List<? extends LanguageCodeStyleProvider> getLanguageCodeStyleProviders() {
    return LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList();
  }
}