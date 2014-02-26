/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageFormatting extends LanguageExtension<FormattingModelBuilder> {
  public static final LanguageFormatting INSTANCE = new LanguageFormatting();

  private LanguageFormatting() {
    super("com.intellij.lang.formatter");
  }

  @Nullable
  public FormattingModelBuilder forContext(@NotNull PsiElement context) {
    return forContext(context.getLanguage(), context);
  }

  @Nullable
  public FormattingModelBuilder forContext(@NotNull Language language, @NotNull PsiElement context) {
    for (LanguageFormattingRestriction each : Extensions.getExtensions(LanguageFormattingRestriction.EXTENSION)) {
      if (!each.isFormatterAllowed(context)) return null;
    }
    for (FormattingModelBuilder builder : allForLanguage(language)) {
      if (builder instanceof CustomFormattingModelBuilder) {
        final CustomFormattingModelBuilder custom = (CustomFormattingModelBuilder)builder;
        if (custom.isEngagedToFormat(context)) return builder;
      }
    }
    return forLanguage(language);
  }
}