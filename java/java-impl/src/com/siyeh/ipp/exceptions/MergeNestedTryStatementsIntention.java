// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class MergeNestedTryStatementsIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("merge.nested.try.statements.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("merge.nested.try.statements.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new NestedTryStatementsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiTryStatement tryStatement1 = (PsiTryStatement)element.getParent();
    final StringBuilder newTryStatement = new StringBuilder("try ");
    final PsiResourceList list1 = tryStatement1.getResourceList();
    int resourceCount = 0;
    if (list1 != null) {
      resourceCount = appendResources(newTryStatement, resourceCount, list1);
    }
    final PsiCodeBlock tryBlock1 = tryStatement1.getTryBlock();
    if (tryBlock1 == null) {
      return;
    }
    final PsiStatement[] statements = tryBlock1.getStatements();
    if (statements.length != 1) {
      return;
    }
    final PsiTryStatement tryStatement2 = (PsiTryStatement)statements[0];
    final PsiResourceList list2 = tryStatement2.getResourceList();
    if (list2 != null) {
      resourceCount = appendResources(newTryStatement, resourceCount, list2);
    }
    if (resourceCount > 0) {
      newTryStatement.append(')');
    }
    final PsiCodeBlock tryBlock2 = tryStatement2.getTryBlock();
    if (tryBlock2 == null) {
      return;
    }
    newTryStatement.append(tryBlock2.getText());
    final PsiCatchSection[] catchSections2 = tryStatement2.getCatchSections();
    for (PsiCatchSection section : catchSections2) {
      newTryStatement.append(section.getText());
    }
    final PsiCatchSection[] catchSections1 = tryStatement1.getCatchSections();
    for (PsiCatchSection section : catchSections1) {
      newTryStatement.append(section.getText());
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiStatement newStatement = factory.createStatementFromText(newTryStatement.toString(), element);
    tryStatement1.replace(newStatement);
  }

  private static int appendResources(StringBuilder newTryStatement, int count, PsiResourceList list) {
    for (PsiResourceListElement resource : list) {
      if (count == 0) newTryStatement.append('(');
      if (count > 0) newTryStatement.append(';');
      newTryStatement.append(resource.getText());
      ++count;
    }
    return count;
  }
}
