// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorTopmost;

public class CastVarPostfixTemplate extends StringBasedPostfixTemplate {
  private static final String TYPE_VAR = "typeVar";
  private static final String VAR_NAME = "varName";

  public CastVarPostfixTemplate() {
    super("castvar", "T name = (T)expr", selectorTopmost(IS_NON_VOID));
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    boolean isFinal = JavaCodeStyleSettings.getInstance(file).GENERATE_FINAL_LOCALS;

    return (isFinal ? "final " : "") + "$" + TYPE_VAR + "$ $" + VAR_NAME + "$ = ($" + TYPE_VAR + "$)$expr$;$END$";
  }

  @Override
  public void setVariables(@NotNull Template template, @NotNull PsiElement element) {
    super.setVariables(template, element);
    if (element instanceof PsiExpression) {
      PsiType[] types = GuessManager.getInstance(element.getProject()).guessTypeToCast((PsiExpression)element);
      fill(template, types, element);
    }
    else {
      template.addVariable(TYPE_VAR, null, null, true, false);
    }

    MacroCallNode nameMacro = new MacroCallNode(new SuggestVariableNameMacro());
    template.addVariable(VAR_NAME, nameMacro, nameMacro, true);
  }

  private static void fill(@NotNull Template template, @NotNull PsiType[] suggestedTypes, @NotNull PsiElement context) {
    Set<LookupElement> itemSet = new LinkedHashSet<>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    final LookupElement[] lookupItems = itemSet.toArray(LookupElement.EMPTY_ARRAY);
    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], context.getProject()) : null;

    Expression expr = new Expression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };

    template.addVariable(TYPE_VAR, expr, expr, true);
  }
}
