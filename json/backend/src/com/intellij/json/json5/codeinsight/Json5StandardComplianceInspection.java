// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5.codeinsight;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.json.JsonDialectUtil;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.json.json5.Json5Language;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public final class Json5StandardComplianceInspection extends JsonStandardComplianceInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!(JsonDialectUtil.getLanguageOrDefaultJson(holder.getFile()) instanceof Json5Language)) return PsiElementVisitor.EMPTY_VISITOR;
    return new StandardJson5ValidatingElementVisitor(holder);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.EMPTY;
  }

  private final class StandardJson5ValidatingElementVisitor extends StandardJsonValidatingElementVisitor {
    StandardJson5ValidatingElementVisitor(ProblemsHolder holder) {
      super(holder);
    }

    @Override
    protected boolean allowComments() {
      return true;
    }

    @Override
    protected boolean allowSingleQuotes() {
      return true;
    }

    @Override
    protected boolean allowIdentifierPropertyNames() {
      return true;
    }

    @Override
    protected boolean allowTrailingCommas() {
      return true;
    }

    @Override
    protected boolean allowNanInfinity() {
      return true;
    }

    @Override
    protected boolean isValidPropertyName(@NotNull PsiElement literal) {
      if (literal instanceof JsonLiteral) {
        String textWithoutHostEscaping = JsonPsiUtil.getElementTextWithoutHostEscaping(literal);
        return textWithoutHostEscaping.startsWith("\"") || textWithoutHostEscaping.startsWith("'");
      }
      if (literal instanceof JsonReferenceExpression) {
        return StringUtil.isJavaIdentifier(literal.getText());
      }
      return false;
    }
  }
}
