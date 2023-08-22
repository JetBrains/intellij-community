/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface LanguageFormattingRestriction {
  ExtensionPointName<LanguageFormattingRestriction> EP_NAME = ExtensionPointName.create(
    "com.intellij.lang.formatter.restriction");

  boolean isFormatterAllowed(@NotNull PsiElement context);

  /**
   * Checks if automatic code reformat is allowed, for example, on save. By default, the method returns the same value as
   * {@link #isFormatterAllowed(PsiElement)} used for explicit reformat.
   *
   * @param context A context element.
   *
   * @return True if automatic reformat is allowed, false to block it. For automatic formatting to work, this method and
   * {@link #isFormatterAllowed(PsiElement)} must <i>both</i> return {@code true}.
   *
   * @see LanguageFormatting#isAutoFormatAllowed(PsiElement)
   */
  default boolean isAutoFormatAllowed(@NotNull PsiElement context) {
    return isFormatterAllowed(context);
  }
}
