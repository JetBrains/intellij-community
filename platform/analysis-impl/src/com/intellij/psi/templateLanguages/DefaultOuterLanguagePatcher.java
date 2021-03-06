package com.intellij.psi.templateLanguages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultOuterLanguagePatcher implements TemplateDataElementType.OuterLanguageRangePatcher {
  public static final String OUTER_EXPRESSION_PLACEHOLDER = "jbIdentifier6b52cc4b";

  @Override
  public @Nullable String getTextForOuterLanguageInsertionRange(@NotNull TemplateDataElementType templateDataElementType,
                                                                @NotNull CharSequence outerElementText) {
    return OUTER_EXPRESSION_PLACEHOLDER;
  }
}
