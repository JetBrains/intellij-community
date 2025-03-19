// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SubtypesMacro extends Macro {
  private static final Logger LOG = Logger.getInstance(SubtypesMacro.class);
  @Override
  public String getName() {
    return "subtypes";
  }

  @Override
  public String getPresentableName() {
    return "subtypes(TYPE)";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length == 0) return LookupElement.EMPTY_ARRAY;
    Result paramResult = params[0].calculateQuickResult(context);
    if (paramResult instanceof PsiTypeResult) {
      return suggestSubTypes(context, ((PsiTypeResult)paramResult).getType());
    }
    if (paramResult instanceof TextResult) {
      PsiElement contextPsi = context.getPsiElementAtStartOffset();
      if (contextPsi != null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
        try {
          return suggestSubTypes(context, factory.createTypeFromText(((TextResult)paramResult).getText(), contextPsi));
        }
        catch (IncorrectOperationException e) {
          LOG.debug(e);
        }
      }
    }
    return LookupElement.EMPTY_ARRAY;
  }

  private static LookupElement[] suggestSubTypes(ExpressionContext context, PsiType type) {
    final PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
    final PsiElement element = file.findElementAt(context.getStartOffset());

    final Set<LookupElement> set = new LinkedHashSet<>();
    JavaTemplateUtil.addTypeLookupItem(set, type);
    CodeInsightUtil.processSubTypes(type, element, false, PrefixMatcher.ALWAYS_TRUE,
                                    psiType -> JavaTemplateUtil.addTypeLookupItem(set, psiType));
    return set.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}