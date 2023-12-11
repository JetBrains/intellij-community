// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceArmWithTryFinallyIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.arm.with.try.finally.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.arm.with.try.finally.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AutomaticResourceManagementPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiTryStatement tryStatement = (PsiTryStatement)token.getParent();
    if (tryStatement == null) {
      return;
    }
    final boolean replaceAll = tryStatement.getCatchBlocks().length == 0 && tryStatement.getFinallyBlock() == null;
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return;
    }
    final List<String> resources = new ArrayList<>();
    final StringBuilder newTryStatement = new StringBuilder("{");
    for (PsiResourceListElement resource : resourceList) {
      if (resource instanceof PsiResourceVariable) {
        newTryStatement.append(resource.getText()).append(";\n");
        resources.add(((PsiResourceVariable)resource).getName());
      }
      else {
        resources.add(resource.getText());
      }
      newTryStatement.append("try {");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final PsiElement[] children = tryBlock.getChildren();
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement child = children[i];
      newTryStatement.append(child.getText());
    }
    for (int i = resources.size() - 1; i >= 0; i--) {
      newTryStatement.append("} finally {\n").append(resources.get(i)).append(".close();\n}");
    }
    newTryStatement.append('}');
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiCodeBlock newCodeBlock = factory.createCodeBlockFromText(newTryStatement.toString(), element);
    if (replaceAll) {
      for (PsiStatement newStatement : newCodeBlock.getStatements()) {
        tryStatement.getParent().addBefore(newStatement, tryStatement);
      }
      tryStatement.delete();
    }
    else {
      resourceList.delete();
      tryBlock.replace(newCodeBlock);
    }
  }
}
