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
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FieldNullabilityCalculator {

  public static final ExtensionPointName<FieldNullabilityCalculator> EP_NAME = ExtensionPointName.create("com.intellij.fieldNullabilityCalculator");

  @Nullable
  public abstract Nullness calculate(@NotNull PsiField field);

  @NotNull
  public static Nullness calculateNullability(@NotNull PsiField field) {
    for (FieldNullabilityCalculator calculator : EP_NAME.getExtensions()) {
      final Nullness nullness = calculator.calculate(field);
      if (nullness != null) {
        return nullness;
      }
    }
    return Nullness.UNKNOWN;
  }
}
