/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class ComponentTypeOfMacro extends Macro {
  public String getName() {
    return "componentTypeOf";
  }

  public String getPresentableName() {
    return CodeInsightBundle.message("macro.component.type.of.array");
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    LookupElement[] lookupItems = params[0].calculateLookupItems(context);
    if (lookupItems == null) return null;

    for (LookupElement element : lookupItems) {
      if (element instanceof LookupItem) {
        final LookupItem item = (LookupItem)element;
        Integer bracketsCount = (Integer)item.getUserData(LookupItem.BRACKETS_COUNT_ATTR);
        if (bracketsCount == null) return null;
        item.putUserData(LookupItem.BRACKETS_COUNT_ATTR, new Integer(bracketsCount.intValue() - 1));
      }
    }

    return lookupItems;
  }

  public Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    if (result instanceof PsiTypeResult) {
      PsiType type = ((PsiTypeResult) result).getType();
      if (type instanceof PsiArrayType) {
        return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
      }
    }

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    PsiType type;
    if (expr == null) {
      type = MacroUtil.resultToPsiType(result, context);
    }
    else{
      type = expr.getType();
    }
    if (type instanceof PsiArrayType) {
      return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
    }

    return new PsiElementResult(null);
  }
}

