// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author Dmitry Avdeev
 */
public final class DummyConversionService extends ConversionService {
  private static final ConversionResult CONVERSION_RESULT = new ConversionResult() {
    @Override
    public boolean conversionNotNeeded() {
      return true;
    }

    @Override
    public boolean openingIsCanceled() {
      return false;
    }

    @Override
    public void postStartupActivity(@NotNull Project project) {
    }
  };

  @NotNull
  @Override
  public ConversionResult convertSilently(@NotNull Path projectPath, @NotNull ConversionListener conversionListener) {
    return CONVERSION_RESULT;
  }

  @NotNull
  @Override
  public ConversionResult convert(@NotNull Path projectPath) {
    return CONVERSION_RESULT;
  }

  @NotNull
  @Override
  public ConversionResult convertModule(@NotNull Project project, @NotNull Path moduleFile) {
    return CONVERSION_RESULT;
  }

  @Override
  public void saveConversionResult(@NotNull Path projectPath) {
  }
}
