// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteCatchFix extends PsiUpdateModCommandAction<PsiParameter> {
  private final String myTypeText;

  public DeleteCatchFix(@NotNull PsiParameter catchParameter) {
    this(catchParameter, JavaHighlightUtil.formatType(catchParameter.getType()));
  }

  private DeleteCatchFix(@NotNull PsiParameter catchParameter, @NotNull String typeText) {
    super(catchParameter);
    myTypeText = typeText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("delete.catch.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameter element) {
    return Presentation.of(QuickFixBundle.message("delete.catch.text", myTypeText));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiParameter catchParameter, @NotNull ModPsiUpdater updater) {
    PsiElement previousElement = deleteCatch(catchParameter);
    if (previousElement != null) {
      //move caret to previous catch section
      updater.moveCaretTo(previousElement.getTextRange().getEndOffset());
    }
  }

  /**
   * Deletes catch section
   *
   * @param catchParameter the catchParameter in the section to delete (must be a catch parameter)
   * @return the physical element before the deleted catch section, if available. Can be used to position the editor cursor after deletion.
   */
  public static PsiElement deleteCatch(PsiParameter catchParameter) {
    final PsiTryStatement tryStatement = ((PsiCatchSection)catchParameter.getDeclarationScope()).getTryStatement();
    if (tryStatement.getCatchBlocks().length == 1 && tryStatement.getFinallyBlock() == null && tryStatement.getResourceList() == null) {
      // unwrap entire try statement
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      PsiElement lastAddedStatement = null;
      if (tryBlock != null) {
        final PsiElement firstElement = tryBlock.getFirstBodyElement();
        if (firstElement != null) {
          final PsiElement tryParent = tryStatement.getParent();
          if (!(tryParent instanceof PsiCodeBlock)) {
            tryStatement.replace(tryBlock);
            return tryBlock;
          }
          boolean mayCompleteNormally = ControlFlowUtils.codeBlockMayCompleteNormally(tryBlock);
          if (!mayCompleteNormally) {
            PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(tryStatement.getNextSibling());
            PsiJavaToken rBrace = ((PsiCodeBlock)tryParent).getRBrace();
            PsiElement lastElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(rBrace);
            if (nextElement != null && lastElement != null && nextElement != rBrace) {
              tryParent.deleteChildRange(nextElement, lastElement);
            }
          }
          final PsiElement lastBodyElement = tryBlock.getLastBodyElement();
          assert lastBodyElement != null : tryBlock.getText();
          tryParent.addRangeBefore(firstElement, lastBodyElement, tryStatement);
          lastAddedStatement = tryStatement.getPrevSibling();
          while (lastAddedStatement != null && (lastAddedStatement instanceof PsiWhiteSpace || lastAddedStatement.getTextLength() == 0)) {
            lastAddedStatement = lastAddedStatement.getPrevSibling();
          }
        }
      }
      tryStatement.delete();

      return lastAddedStatement;
    }

    // delete catch section
    final PsiElement catchSection = catchParameter.getParent();
    assert catchSection instanceof PsiCatchSection : catchSection;
    //save previous element to move caret to
    PsiElement previousElement = catchSection.getPrevSibling();
    while (previousElement instanceof PsiWhiteSpace) {
      previousElement = previousElement.getPrevSibling();
    }
    catchSection.delete();
    return previousElement;
  }
}
