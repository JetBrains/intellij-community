package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.util.PsiUtil;

import java.util.LinkedHashSet;
import java.util.Set;

public class TypeExpression implements Expression {
  private final LookupItem[] myItems;
  protected SmartTypePointer myDefaultType;

  public TypeExpression(final Project project, PsiType[] types) {
    final Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiType type : types) {
      LookupItemUtil.addLookupItem(set, type, "");
    }

    myItems = set.toArray(new LookupItem[set.size()]);
    final PsiType psiType = PsiUtil.convertAnonymousToBaseType(types[0]);
    myDefaultType = SmartPointerManager.getInstance(project).createSmartTypePointer(psiType);

  }

  public Result calculateResult(ExpressionContext context) {
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return new PsiTypeResult(myDefaultType.getType(), project);
  }

  public Result calculateQuickResult(ExpressionContext context) {
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return new PsiTypeResult(myDefaultType.getType(), project);
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    if (myItems.length <= 1) return null;
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    return myItems;
  }

}
