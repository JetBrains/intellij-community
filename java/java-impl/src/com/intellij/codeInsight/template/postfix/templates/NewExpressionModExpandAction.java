// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.completion.modcommand.ClassReferenceCompletionItem;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NewExpressionModExpandAction implements ExpressionSelectorModExpander.ModExpandAction {
  private final @NotNull ExpressionSelectorModExpander.ModExpandAction myFallback;

  NewExpressionModExpandAction(@NotNull ExpressionSelectorModExpander.ModExpandAction fallback) {
    myFallback = fallback;
  }

  @Override
  public void expand(@NotNull ActionContext ctx, @NotNull ModPsiUpdater updater, @NotNull PsiElement elementInCopy) {
    if (elementInCopy instanceof PsiReferenceExpression writableExpr) {
      PsiClass psiClass = resolveClass(writableExpr);
      if (psiClass == null) {
        myFallback.expand(ctx, updater, elementInCopy);
        return;
      }
      String expressionText = writableExpr.getText();
      Document document = updater.getDocument();
      boolean hasTypeParameters = psiClass.hasTypeParameters();
      String diamond = hasTypeParameters ? "<>" : "";

      int classNameStart = replaceExpressionTextByNewExpression(writableExpr, document);
      int classNameEnd = classNameStart + expressionText.length();

      // Commit to resolve PsiNewExpression and check context (mirrors shouldInsertParentheses in the editor path)
      PsiDocumentManager pdm = PsiDocumentManager.getInstance(ctx.project());
      pdm.commitDocument(document);
      PsiElement newKeyword = updater.getPsiFile().findElementAt(classNameStart - "new ".length());
      PsiNewExpression newExpr = newKeyword != null ? (PsiNewExpression)newKeyword.getParent() : null;
      boolean addParens = newExpr == null ||
                          !DumbService.getInstance(ctx.project())
                            .computeWithAlternativeResolveEnabled(
                              () -> JavaCompletionUtil.isArrayTypeExpected(newExpr));

      if (addParens) {
        document.insertString(classNameEnd, diamond + "()");
      }

      updater.moveCaretTo(classNameEnd);

      ClassReferenceCompletionItem classItem = new ClassReferenceCompletionItem(psiClass);
      classItem.update(ctx.withOffset(classNameEnd), ModCompletionItem.DEFAULT_INSERTION_CONTEXT, updater);

      pdm.commitDocument(document);
      JavaCodeStyleManager.getInstance(ctx.project())
        .shortenClassReferences(updater.getPsiFile(), classNameStart, updater.getCaretOffset());

      if (addParens) {
        if (hasTypeParameters) {
          updater.moveCaretTo(updater.getCaretOffset() + 1);
          int caretInDiamond = updater.getCaretOffset();
          updater.registerTabOut(TextRange.create(caretInDiamond, caretInDiamond), caretInDiamond + 2);
        }
        else {
          boolean hasArgConstructor = ContainerUtil.exists(psiClass.getConstructors(),
                                                           c -> c.getParameterList().getParametersCount() > 0);
          updater.moveCaretTo(updater.getCaretOffset() + (hasArgConstructor ? 1 : 2));
        }
      }
      return;
    }
    myFallback.expand(ctx, updater, elementInCopy);
  }

  static @Nullable PsiClass resolveClass(@NotNull PsiReferenceExpression ref) {
    JavaResolveResult result = DumbService.getInstance(ref.getProject())
      .withAlternativeResolveEnabled(() -> ref.advancedResolve(true));
    PsiElement element = result.getElement();

    if (element == null) {
      String name = ref.getReferenceName();
      if (name != null && ref.getQualifierExpression() == null) {
        PsiClass[] classes = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
          () -> PsiShortNamesCache.getInstance(ref.getProject()).getClassesByName(name, ref.getResolveScope()));
        if (classes.length == 1) {
          element = classes[0];
        }
      }
    }

    return element instanceof PsiClass psiClass ? psiClass : null;
  }

  static int replaceExpressionTextByNewExpression(@NotNull PsiElement expression, @NotNull Document document) {
    TextRange range = expression.getTextRange();
    String newPrefix = "new ";
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newPrefix + expression.getText());
    return range.getStartOffset() + newPrefix.length();
  }
}
