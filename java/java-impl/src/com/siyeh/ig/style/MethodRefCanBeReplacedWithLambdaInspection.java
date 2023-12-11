// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodRefCanBeReplacedWithLambdaInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodRefToLambdaVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)infos[0];
    final boolean onTheFly = (Boolean)infos[1];
    if (LambdaRefactoringUtil.canConvertToLambdaWithoutSideEffects(methodReferenceExpression)) {
      return new MethodRefToLambdaFix();
    }
    else if (onTheFly) {
      return new SideEffectsMethodRefToLambdaFix();
    }
    return null;
  }

  private static class MethodRefToLambdaVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      super.visitMethodReferenceExpression(methodReferenceExpression);
      if (LambdaRefactoringUtil.canConvertToLambda(methodReferenceExpression)) {
        registerError(methodReferenceExpression, methodReferenceExpression, isOnTheFly());
      }
    }
  }

  private static class MethodRefToLambdaFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiMethodReferenceExpression methodRef) {
        LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
      }
    }
  }

  private static class SideEffectsMethodRefToLambdaFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return ApplicationManager.getApplication().isUnitTestMode() ?
             (InspectionGadgetsBundle.message("side.effects.method.ref.to.lambda.fix.family.name",
                                              InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix"))) :
             InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      PsiMethodReferenceExpression methodRef = ObjectUtils.tryCast(previewDescriptor.getPsiElement(), PsiMethodReferenceExpression.class);
      if (methodRef == null) {
        return IntentionPreviewInfo.EMPTY;
      }
      LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiMethodReferenceExpression methodRef) {
        DataManager.getInstance()
          .getDataContextFromFocusAsync()
          .onSuccess(context -> {
            final Editor editor = CommonDataKeys.EDITOR.getData(context);
            if (editor != null) {
              CommandProcessor.getInstance()
                .executeCommand(project, () -> doFixAndRemoveSideEffects(editor, methodRef), getFamilyName(), null);
            }
          });
      }
    }

    private static void doFixAndRemoveSideEffects(@NotNull Editor editor, @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(methodReferenceExpression)) return;
      final PsiLambdaExpression lambdaExpression =
        WriteAction.compute(() -> LambdaRefactoringUtil.convertMethodReferenceToLambda(methodReferenceExpression, false, true));
      if (lambdaExpression != null) {
        LambdaRefactoringUtil.removeSideEffectsFromLambdaBody(editor, lambdaExpression);
      }
    }
  }
}
