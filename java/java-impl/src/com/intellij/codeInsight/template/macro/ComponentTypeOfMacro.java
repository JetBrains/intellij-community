// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ComponentTypeOfMacro extends Macro {
  @Override
  public String getName() {
    return "componentTypeOf";
  }

  @Override
  public String getPresentableName() {
    return JavaBundle.message("macro.component.type.of.array");
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length != 1) return null;
    LookupElement[] lookupItems = params[0].calculateLookupItems(context);
    if (lookupItems == null) return null;

    List<LookupElement> result = new ArrayList<>();
    for (LookupElement element : lookupItems) {
      PsiTypeLookupItem lookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
      if (lookupItem != null) {
        PsiType psiType = lookupItem.getType();
        if (psiType instanceof PsiArrayType) {
          result.add(PsiTypeLookupItem.createLookupItem(((PsiArrayType)psiType).getComponentType(), null));
        }
      }
    }

    return lookupItems;
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    if (result instanceof PsiTypeResult) {
      PsiType type = ((PsiTypeResult) result).getType();
      if (type instanceof PsiArrayType) {
        return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
      }
    }

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    PsiType type = expr == null ? MacroUtil.resultToPsiType(result, context) : expr.getType();
    if (type instanceof PsiArrayType) {
      return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
    }

    LookupElement[] elements = params[0].calculateLookupItems(context);
    if (elements != null) {
      for (LookupElement element : elements) {
        PsiTypeLookupItem typeLookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
        if (typeLookupItem != null) {
          PsiType psiType = typeLookupItem.getType();
          if (psiType instanceof PsiArrayType) {
            return new PsiTypeResult(((PsiArrayType)psiType).getComponentType(), context.getProject());
          }
        }
      }
    }

    return new PsiElementResult(null);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}

