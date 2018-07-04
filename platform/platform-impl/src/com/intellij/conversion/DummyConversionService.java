/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.conversion;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class DummyConversionService extends ConversionService {

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
  public ConversionResult convertSilently(@NotNull String projectPath) {
    return CONVERSION_RESULT;
  }

  @NotNull
  @Override
  public ConversionResult convertSilently(@NotNull String projectPath, @NotNull ConversionListener conversionListener) {
    return CONVERSION_RESULT;
  }

  @NotNull
  @Override
  public ConversionResult convert(@NotNull String projectPath) {
    return CONVERSION_RESULT;
  }

  @NotNull
  @Override
  public ConversionResult convertModule(@NotNull Project project, @NotNull File moduleFile) {
    return CONVERSION_RESULT;
  }

  @Override
  public void saveConversionResult(@NotNull String projectPath) {
  }
}
