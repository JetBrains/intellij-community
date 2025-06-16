// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public final class MethodRefCanBeReplacedWithLambdaInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS, JavaFeature.METHOD_REFERENCES);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodRefToLambdaVisitor();
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)infos[0];
    return LocalQuickFix.from(new SideEffectsMethodRefToLambdaFix(methodReferenceExpression));
  }

  private static class MethodRefToLambdaVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      super.visitMethodReferenceExpression(methodReferenceExpression);
      if (LambdaRefactoringUtil.canConvertToLambda(methodReferenceExpression)) {
        registerError(methodReferenceExpression, methodReferenceExpression);
      }
    }
  }

  private static class SideEffectsMethodRefToLambdaFix extends PsiBasedModCommandAction<PsiMethodReferenceExpression> {
    private final ThreeState myExtractSideEffect;

    SideEffectsMethodRefToLambdaFix(@NotNull PsiMethodReferenceExpression methodRef) {
      this(methodRef, ThreeState.UNSURE);
    }

    SideEffectsMethodRefToLambdaFix(@NotNull PsiMethodReferenceExpression methodRef, @NotNull ThreeState extractSideEffect) {
      super(methodRef);
      myExtractSideEffect = extractSideEffect;
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression element) {
      String message = switch (myExtractSideEffect) {
        case YES -> InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix.side.effects");
        case NO -> InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix.no.side.effects");
        case UNSURE -> InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
      };
      return Presentation.of(message);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression methodRef) {
      boolean extractSideEffect;
      if (myExtractSideEffect == ThreeState.UNSURE) {
        if (possibleToExtractSideEffect(methodRef)) {
          //noinspection DialogTitleCapitalization
          return ModCommand.chooseAction(getFamilyName(),
                                         new SideEffectsMethodRefToLambdaFix(methodRef, ThreeState.YES),
                                         new SideEffectsMethodRefToLambdaFix(methodRef, ThreeState.NO));
        }
        extractSideEffect = false;
      }
      else {
        extractSideEffect = myExtractSideEffect == ThreeState.YES && possibleToExtractSideEffect(methodRef);
      }
      if (!extractSideEffect) {
        return ModCommand.psiUpdate(methodRef, mr -> LambdaRefactoringUtil.convertMethodReferenceToLambda(mr, false, true));
      }
      return ModCommand.psiUpdate(methodRef, (mr, updater) -> {
        CodeBlockSurrounder surrounder = requireNonNull(CodeBlockSurrounder.forExpression(mr));
        CodeBlockSurrounder.SurroundResult result = surrounder.surround();
        PsiLambdaExpression lambdaExpression =
          LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)result.getExpression(), false, true);
        if (lambdaExpression == null) return;
        PsiExpression methodCall = LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody());
        PsiExpression qualifierExpression = null;
        if (methodCall instanceof PsiMethodCallExpression call) {
          qualifierExpression = call.getMethodExpression().getQualifierExpression();
        }
        else if (methodCall instanceof PsiNewExpression call) {
          qualifierExpression = call.getQualifier();
        }
        if (qualifierExpression == null) return;
        PsiType type = qualifierExpression.getType();
        if (type == null) return;
        List<String> varNames =
          new VariableNameGenerator(result.getAnchor(), VariableKind.LOCAL_VARIABLE).byExpression(qualifierExpression).generateAll(true);
        String name = varNames.get(0);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
        PsiDeclarationStatement declaration = factory
          .createVariableDeclarationStatement(name, type, qualifierExpression, result.getAnchor());
        declaration = (PsiDeclarationStatement)result.getAnchor().getParent().addBefore(declaration, result.getAnchor());
        PsiVariable declaredVariable = (PsiVariable)declaration.getDeclaredElements()[0];
        qualifierExpression.replace(factory.createExpressionFromText(name, null));
        updater.rename(declaredVariable, varNames);
      });
    }

    private static boolean possibleToExtractSideEffect(@NotNull PsiMethodReferenceExpression methodRef) {
      PsiExpression qualifier = methodRef.getQualifierExpression();
      return qualifier != null && qualifier.getType() != null && 
             (SideEffectChecker.mayHaveSideEffects(qualifier) || ExpressionUtils.isNewObject(qualifier)) &&
             CodeBlockSurrounder.canSurround(methodRef);
    }
  }
}
