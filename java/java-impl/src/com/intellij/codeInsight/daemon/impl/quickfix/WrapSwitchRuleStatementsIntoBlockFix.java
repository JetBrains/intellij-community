// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class WrapSwitchRuleStatementsIntoBlockFix extends PsiUpdateModCommandAction<PsiSwitchLabeledRuleStatement> {
  public WrapSwitchRuleStatementsIntoBlockFix(@NotNull PsiSwitchLabeledRuleStatement ruleStatement) {
    super(ruleStatement);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.block");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiSwitchLabeledRuleStatement ruleStatement) {
    if (ruleStatement.getBody() instanceof PsiBlockStatement) return null;
    PsiStatement sibling = PsiTreeUtil.getNextSiblingOfType(ruleStatement, PsiStatement.class);
    if (sibling == null || sibling instanceof PsiSwitchLabelStatementBase) {
      return Presentation.of(getFamilyName()).withFixAllOption(this);
    }
    return Presentation.of(QuickFixBundle.message("wrap.with.block")).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchLabeledRuleStatement ruleStatement, @NotNull ModPsiUpdater updater) {
    if (!ruleStatement.isValid()) return;
    PsiCodeBlock parent = ObjectUtils.tryCast(ruleStatement.getParent(), PsiCodeBlock.class);
    if (parent == null) return;
    PsiJavaToken rBrace = parent.getRBrace();
    PsiElement[] children = parent.getChildren();
    int index = ArrayUtil.indexOf(children, ruleStatement);
    assert index >= 0;
    int nextIndex = index + 1;
    while (nextIndex < children.length && !(children[nextIndex] instanceof PsiSwitchLabelStatementBase) && children[nextIndex] != rBrace) {
      nextIndex++;
    }
    if (children[nextIndex - 1] instanceof PsiWhiteSpace) {
      nextIndex--;
    }
    PsiElement oldBody = null;
    if (ruleStatement.getBody() != null) {
      oldBody = ruleStatement.getBody().copy();
      ruleStatement.getBody().delete();
    }
    for (PsiElement lastChild = ruleStatement.getLastChild(); lastChild != null; lastChild = lastChild.getPrevSibling()) {
      if (PsiUtil.isJavaToken(lastChild, JavaTokenType.SEMICOLON)) {
        lastChild.delete();
        break;
      }
    } 
    PsiSwitchLabeledRuleStatement newRule = (PsiSwitchLabeledRuleStatement)JavaPsiFacade.getElementFactory(context.project())
      .createStatementFromText(ruleStatement.getText() + "{}", ruleStatement);
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
    ruleStatement.replace(newRule);
  }
}
