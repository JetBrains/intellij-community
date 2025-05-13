// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NotNull;

public final class StringArgsMacro extends Macro {

  @Override
  public String getName() {
    return "stringArgs";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    return null;
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, final ExpressionContext context) {
    return new LookupElement[]{LookupElementBuilder.create("String[] args")};
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType.NormalClassDeclarationAfterShortMainMethod ||
           context instanceof JavaCodeContextType.ImplicitClassDeclaration;
  }

  @Override
  public @NotNull LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.UNFOCUSED;
  }
}
