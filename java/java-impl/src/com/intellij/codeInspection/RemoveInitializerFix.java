/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveInitializerFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.unused.assignment.remove.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiInitializer = descriptor.getPsiElement();
    if (!(psiInitializer instanceof PsiExpression)) return;
    if (!(psiInitializer.getParent() instanceof PsiVariable)) return;

    final PsiVariable variable = (PsiVariable)psiInitializer.getParent();
    sideEffectAwareRemove(project, (PsiExpression)psiInitializer, psiInitializer, variable);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void sideEffectAwareRemove(Project project,
                                           PsiExpression psiInitializer,
                                           PsiElement elementToDelete,
                                           PsiVariable variable) {
    PsiTypeElement typeElement = variable.getTypeElement();
    sideEffectAwareRemove(project, psiInitializer, elementToDelete, variable,
                          (typeElement != null ? typeElement.getText() + " " + variable.getName() + ";<br>" : "") +
                          PsiExpressionTrimRenderer.render(psiInitializer));
  }

  /**
   * Remove an element. Ask user what to do if the element has a side effect: keep the side effect, ignore it, or cancel removal.
   * @return <code>true</code> if the element was actually removed, <code>false</code> if removal was cancelled or is not possible.
   * */
  public static boolean sideEffectAwareRemove(Project project,
                                              PsiExpression psiInitializer,
                                              PsiElement elementToDelete,
                                              PsiVariable variable,
                                              String afterText) {
    if (!FileModificationService.getInstance().prepareFileForWrite(elementToDelete.getContainingFile())) return false;

    final List<PsiElement> sideEffects = new ArrayList<>();
    boolean hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(psiInitializer, variable, sideEffects);
    final PsiElement declaration = variable.getParent();
    RemoveUnusedVariableUtil.RemoveMode res;
    if (hasSideEffects) {
      hasSideEffects = PsiUtil.isStatement(psiInitializer);
      res = RemoveUnusedVariableFix.showSideEffectsWarning(sideEffects, variable,
                                                           FileEditorManager.getInstance(project).getSelectedTextEditor(),
                                                           hasSideEffects, sideEffects.get(0).getText(), afterText);
      if (res == RemoveUnusedVariableUtil.RemoveMode.CANCEL) {
        return false;
      }
    }
    else {
      res = RemoveUnusedVariableUtil.RemoveMode.DELETE_ALL;
    }
    WriteAction.run(() -> doRemove(project, psiInitializer, elementToDelete, variable, declaration, res));
    return true;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiExpression psiInitializer = ObjectUtils.tryCast(previewDescriptor.getPsiElement(), PsiExpression.class);
    if (psiInitializer == null) return IntentionPreviewInfo.EMPTY;
    PsiVariable variable = ObjectUtils.tryCast(psiInitializer.getParent(), PsiVariable.class);
    if (variable == null) return IntentionPreviewInfo.EMPTY;
    RemoveUnusedVariableUtil.RemoveMode res = RemoveUnusedVariableUtil.getModeForPreview(psiInitializer, variable);
    doRemove(project, psiInitializer, psiInitializer, variable, variable.getParent(), res);
    return IntentionPreviewInfo.DIFF;
  }

  static void doRemove(@NotNull Project project,
                       @NotNull PsiExpression psiInitializer,
                       @NotNull PsiElement elementToDelete,
                       @NotNull PsiVariable variable,
                       PsiElement declaration,
                       @NotNull RemoveUnusedVariableUtil.RemoveMode res) {
    if (res == RemoveUnusedVariableUtil.RemoveMode.DELETE_ALL) {
      if (elementToDelete instanceof PsiExpression && !ExpressionUtils.isVoidContext((PsiExpression)elementToDelete) &&
          !PsiTreeUtil.isAncestor(variable, elementToDelete, true)) {
        String name = variable.getName();
        if (name != null) {
          new CommentTracker().replaceAndRestoreComments(elementToDelete, name);
          return;
        }
      }
      elementToDelete.delete();
    }
    else if (res == RemoveUnusedVariableUtil.RemoveMode.MAKE_STATEMENT) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiStatement statementFromText = factory.createStatementFromText(psiInitializer.getText() + ";", null);
      final PsiElement parent = elementToDelete.getParent();
      if (parent instanceof PsiExpressionStatement) {
        parent.replace(statementFromText);
      }
      else {
        elementToDelete.delete();
        if (declaration instanceof PsiClass) {
          PsiClassInitializer initializer = factory.createClassInitializer();
          initializer = (PsiClassInitializer)declaration.addAfter(initializer, variable);
          initializer.getBody().add(statementFromText);
          return;
        }
        PsiElement grandParent = declaration.getParent();
        BlockUtils.addBefore(((PsiStatement)(grandParent instanceof PsiForStatement ? grandParent : declaration)), statementFromText);
      }
    }
  }
}
