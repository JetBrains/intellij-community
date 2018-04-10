// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class TypeExpression extends Expression {
  private final LinkedHashSet<SmartTypePointer> myItems = new LinkedHashSet<>();

  public TypeExpression(@NotNull Project project, @NotNull PsiType[] types) {
    final SmartTypePointerManager manager = SmartTypePointerManager.getInstance(project);
    for (PsiType type : types) {
      myItems.add(manager.createSmartTypePointer(type));
    }
  }

  public TypeExpression(@NotNull Project project, @NotNull Iterable<PsiType> types) {
    final SmartTypePointerManager manager = SmartTypePointerManager.getInstance(project);
    for (PsiType type : types) {
      myItems.add(manager.createSmartTypePointer(type));
    }
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (myItems.isEmpty()) return null;

    final PsiType type = myItems.iterator().next().getType();
    return type == null ? null : new PsiTypeResult(type, project) {
      @Override
      public void handleRecalc(PsiFile psiFile, Document document, int segmentStart, int segmentEnd) {
        if (myItems.size() <= 1) {
          super.handleRecalc(psiFile, document, segmentStart, segmentEnd);
        }
        else {
          JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd, true);
        }
      }
    };
  }

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myItems.size() <= 1) return null;
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    
    List<LookupElement> result = new ArrayList<>(myItems.size());
    for (final SmartTypePointer item : myItems) {
      final PsiType type = item.getType();
      if (type != null) {
        result.add(PsiTypeLookupItem.createLookupItem(type, null));
      }
    }
    return result.toArray(LookupElement.EMPTY_ARRAY);
  }

}
