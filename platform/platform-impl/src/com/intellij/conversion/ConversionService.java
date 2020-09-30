// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public abstract class ConversionService {
  public static @Nullable ConversionService getInstance() {
    return ApplicationManager.getApplication().getService(ConversionService.class);
  }

  @NotNull
  public abstract ConversionResult convertSilently(@NotNull Path projectPath, @NotNull ConversionListener conversionListener);

  public abstract @NotNull ConversionResult convert(@NotNull Path projectPath) throws CannotConvertException;

  @NotNull
  public abstract ConversionResult convertModule(@NotNull Project project, @NotNull Path moduleFile);

  public abstract void saveConversionResult(@NotNull Path projectPath);
}
