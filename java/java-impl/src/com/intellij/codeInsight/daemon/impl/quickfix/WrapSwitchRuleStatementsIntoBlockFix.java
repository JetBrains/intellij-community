// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class WrapSwitchRuleStatementsIntoBlockFix extends BaseIntentionAction {
  @NotNull
  private final PsiSwitchLabeledRuleStatement myRuleStatement;

  public WrapSwitchRuleStatementsIntoBlockFix(@NotNull PsiSwitchLabeledRuleStatement ruleStatement) {
    myRuleStatement = ruleStatement;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.block");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!myRuleStatement.isValid()) return false;
    if (myRuleStatement.getBody() instanceof PsiBlockStatement) return false;
    PsiStatement sibling = PsiTreeUtil.getNextSiblingOfType(myRuleStatement, PsiStatement.class);
    if (sibling == null || sibling instanceof PsiSwitchLabelStatementBase) {
      setText(getFamilyName());
    } else {
      setText(QuickFixBundle.message("wrap.with.block"));
    }
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!myRuleStatement.isValid()) return;
    PsiCodeBlock parent = ObjectUtils.tryCast(myRuleStatement.getParent(), PsiCodeBlock.class);
    if (parent == null) return;
    PsiJavaToken rBrace = parent.getRBrace();
    PsiElement[] children = parent.getChildren();
    int index = ArrayUtil.indexOf(children, myRuleStatement);
    assert index >= 0;
    int nextIndex = index + 1;
    while (nextIndex < children.length && !(children[nextIndex] instanceof PsiSwitchLabelStatementBase) && children[nextIndex] != rBrace) {
      nextIndex++;
    }
    if (children[nextIndex - 1] instanceof PsiWhiteSpace) {
      nextIndex--;
    }
    PsiElement oldBody = null;
    if (myRuleStatement.getBody() != null) {
      oldBody = myRuleStatement.getBody().copy();
      myRuleStatement.getBody().delete();
    }
    PsiSwitchLabeledRuleStatement newRule = (PsiSwitchLabeledRuleStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(
        myRuleStatement.getText() + "{}", myRuleStatement);
    PsiCodeBlock block = ((PsiBlockStatement)Objects.requireNonNull(newRule.getBody())).getCodeBlock();
    if (oldBody != null) {
      block.add(oldBody);
    }
    if (nextIndex > index + 1) {
      PsiElement first = children[index + 1];
      PsiElement last = children[nextIndex - 1];
      block.addRange(first, last);
      parent.deleteChildRange(first, last);
    }
    myRuleStatement.replace(newRule);
  }
}
