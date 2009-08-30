package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.SmartTypePointerManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TypeExpression extends Expression {
  private final Set<SmartTypePointer> myItems;

  public TypeExpression(final Project project, PsiType[] types) {
    final SmartTypePointerManager manager = SmartTypePointerManager.getInstance(project);
    myItems = new LinkedHashSet<SmartTypePointer>();
    for (final PsiType type : types) {
      myItems.add(manager.createSmartTypePointer(type));
    }
  }

  public Result calculateResult(ExpressionContext context) {
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (myItems.isEmpty()) return null;

    final PsiType type = myItems.iterator().next().getType();
    return type == null? null : new PsiTypeResult(type, project);
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myItems.size() <= 1) return null;
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    
    List<LookupItem> result = new ArrayList<LookupItem>(myItems.size());
    for (final SmartTypePointer item : myItems) {
      final PsiType type = item.getType();
      if (type != null) {
        LookupItemUtil.addLookupItem(result, type);
      }
    }
    return result.toArray(new LookupItem[result.size()]);
  }

}
