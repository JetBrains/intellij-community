/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.psi.PsiElement;

import java.util.List;

public class LanguageFormatting extends LanguageExtension<FormattingModelBuilder> {
  public static final LanguageFormatting INSTANCE = new LanguageFormatting();

  private LanguageFormatting() {
    super("com.intellij.lang.formatter");
  }

  public FormattingModelBuilder forContext(PsiElement context) {
    return forContext(context.getLanguage(), context);
  }

  public FormattingModelBuilder forContext(Language language, PsiElement context) {
    final List<FormattingModelBuilder> builders = allForLanguage(language);
    for (FormattingModelBuilder builder : builders) {
      if (builder instanceof CustomFormattingModelBuilder) {
        final CustomFormattingModelBuilder custom = (CustomFormattingModelBuilder)builder;
        if (custom.isEngagedToFormat(context)) return builder;
      }
    }

    return forLanguage(language);
  }
}