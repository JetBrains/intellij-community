/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class DeleteSideEffectsAwareFix extends LocalQuickFixAndIntentionActionOnPsiElement implements LowPriorityAction {
  private final SmartPsiElementPointer<PsiStatement> myStatementPtr;
  private final SmartPsiElementPointer<PsiExpression> myExpressionPtr;
  private final @IntentionName String myMessage;
  private final boolean myIsAvailable;

  public DeleteSideEffectsAwareFix(@NotNull PsiStatement statement, PsiExpression expression) {
    this(statement, expression, false);
  }

  public DeleteSideEffectsAwareFix(@NotNull PsiStatement statement, PsiExpression expression, boolean alwaysAvailable) {
    super(statement);
    SmartPointerManager manager = SmartPointerManager.getInstance(statement.getProject());
    myStatementPtr = manager.createSmartPsiElementPointer(statement);
    myExpressionPtr = manager.createSmartPsiElementPointer(expression);
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
    if (sideEffects.isEmpty()) {
      JavaElementKind kind = statement instanceof PsiExpressionStatement ? JavaElementKind.EXPRESSION : JavaElementKind.STATEMENT;
      myMessage = CommonQuickFixBundle.message("fix.remove.title", kind.object());
    }
    else {
      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
      if (statements.length == 1 && statements[0] instanceof PsiIfStatement) {
        myMessage = QuickFixBundle.message("extract.side.effects.convert.to.if");
      }
      else {
        myMessage = QuickFixBundle.message("extract.side.effects", statements.length);
      }
    }
    myIsAvailable = alwaysAvailable ||
                    // "Remove unnecessary parentheses" action is already present which will do the same
                    sideEffects.size() != 1 || !(statement instanceof PsiExpressionStatement) ||
                    sideEffects.get(0) != PsiUtil.skipParenthesizedExprDown(expression);
  }

  @NotNull
  @Override
  public String getText() {
    return myMessage;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("extract.side.effects.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myIsAvailable;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiStatement statement = myStatementPtr.getElement();
    if (statement == null) return;
    PsiExpression expression = myExpressionPtr.getElement();
    if (expression == null) return;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
    CommentTracker ct = new CommentTracker();
    sideEffects.forEach(ct::markUnchanged);
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
    if (statements.length > 0) {
      PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
      statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
    }
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiStatement &&
        !(parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() == statement) &&
        !(parent instanceof PsiForStatement && ((PsiForStatement)parent).getUpdate() == statement)) {
      ct.replaceAndRestoreComments(statement, "{}");
    } else {
      ct.deleteAndRestoreComments(statement);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiExpression expression = myExpressionPtr.getElement();
    PsiStatement statement = myStatementPtr.getElement();
    if (expression == null || statement == null) return null;
    return new DeleteSideEffectsAwareFix(PsiTreeUtil.findSameElementInCopy(statement, target),
                                         PsiTreeUtil.findSameElementInCopy(expression, target));
  }
}
