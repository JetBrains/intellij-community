// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.Formatter;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageFormatting extends LanguageExtension<FormattingModelBuilder> {
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
    for (LanguageFormattingRestriction each : LanguageFormattingRestriction.EP_NAME.getExtensionList()) {
      if (!each.isFormatterAllowed(context)) return null;
    }
    FormattingModelBuilder builder = findCustomFormattingModelBuilder(language, context);
    if (builder == null) {
      builder = forLanguage(language);
    }
    PsiFile file = context.getContainingFile();
    return ExternalFormatProcessor.useExternalFormatter(file)
           ? Formatter.getInstance().createExternalFormattingModelBuilder(file, builder)
           : builder;
  }

  @Nullable
  private FormattingModelBuilder findCustomFormattingModelBuilder(@NotNull Language language, @NotNull PsiElement context) {
    for (FormattingModelBuilder builder : allForLanguage(language)) {
      if (builder instanceof CustomFormattingModelBuilder) {
        final CustomFormattingModelBuilder custom = (CustomFormattingModelBuilder)builder;
        if (custom.isEngagedToFormat(context)) return builder;
      }
    }
    return null;
  }
}