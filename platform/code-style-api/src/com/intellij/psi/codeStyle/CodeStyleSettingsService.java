// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public interface CodeStyleSettingsService {
  static CodeStyleSettingsService getInstance() {
    return ApplicationManager.getApplication().getService(CodeStyleSettingsService.class);
  }

  void addListener(@NotNull CodeStyleSettingsServiceListener listener, @Nullable Disposable disposable);

  @NotNull
  List<? extends FileTypeIndentOptionsFactory> getFileTypeIndentOptionsFactories();

  @NotNull
  List<? extends CustomCodeStyleSettingsFactory> getCustomCodeStyleSettingsFactories();

  @NotNull
  List<? extends LanguageCodeStyleProvider> getLanguageCodeStyleProviders();
}