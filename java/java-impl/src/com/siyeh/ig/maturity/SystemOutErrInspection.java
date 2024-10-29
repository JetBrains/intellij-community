// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_SYSTEM;

/**
 * @author Bas Leijdekkers
 */
public final class SystemOutErrInspection extends BaseInspection {

  public ConvertSystemOutToLogCallFix.PopularLogLevel myErrLogLevel = ConvertSystemOutToLogCallFix.PopularLogLevel.ERROR;
  public ConvertSystemOutToLogCallFix.PopularLogLevel myOutLogLevel = ConvertSystemOutToLogCallFix.PopularLogLevel.INFO;


  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.dropdown(
        "myErrLogLevel",
        InspectionGadgetsBundle.message("use.system.out.err.problem.fix.err.option"),
        ConvertSystemOutToLogCallFix.PopularLogLevel.class,
        level -> level.toMethodName()),
      OptPane.dropdown(
        "myOutLogLevel",
        InspectionGadgetsBundle.message("use.system.out.err.problem.fix.out.option"),
        ConvertSystemOutToLogCallFix.PopularLogLevel.class,
        level -> level.toMethodName())
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    List<LocalQuickFix> fixes = new ArrayList<>();

    final PsiElement context = (PsiElement)infos[0];

    SuppressForTestsScopeFix testsScopeFix = SuppressForTestsScopeFix.build(this, context);
    if(testsScopeFix != null) {
      fixes.add(testsScopeFix);
    }

    if (context instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.getParent() instanceof PsiReferenceExpression probablyReferenceToCall &&
        probablyReferenceToCall.getParent() instanceof PsiMethodCallExpression callExpression) {

      String name = referenceExpression.getReferenceName();
      String methodName = null;
      if (HardcodedMethodConstants.OUT.equals(name)) {
        methodName = myOutLogLevel.name().toLowerCase(Locale.ROOT);
      }
      else if (HardcodedMethodConstants.ERR.equals(name)) {
        methodName = myErrLogLevel.name().toLowerCase(Locale.ROOT);
      }

      if (methodName != null) {
        ModCommandAction fix = ConvertSystemOutToLogCallFix.createFix(callExpression, methodName);
        if (fix != null) {
          LocalQuickFix localQuickFix = ModCommandService.getInstance().wrapToQuickFix(fix);
          fixes.add(localQuickFix);
        }
      }
    }

    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String getID() {
    return "UseOfSystemOutOrSystemErr";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.system.out.err.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SystemOutErrVisitor();
  }

  private class SystemOutErrVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression.getQualifierExpression() instanceof PsiReferenceExpression referenceExpression) {
        inspectReferenceIfItIsSystemOutErr(referenceExpression);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getParent() instanceof PsiReferenceExpression parentReferenceExpression &&
          parentReferenceExpression.getParent() instanceof PsiMethodCallExpression) {
        return;
      }
      inspectReferenceIfItIsSystemOutErr(expression);
    }

    private void inspectReferenceIfItIsSystemOutErr(@NotNull PsiReferenceExpression expression) {
      final String name = expression.getReferenceName();
      if (!HardcodedMethodConstants.OUT.equals(name) &&
          !HardcodedMethodConstants.ERR.equals(name)) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField field)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!JAVA_LANG_SYSTEM.equals(className)) {
        return;
      }

      if (expression.getParent() instanceof PsiReferenceExpression probablyReferenceToCall &&
          probablyReferenceToCall.getParent() instanceof PsiMethodCallExpression callExpression) {

        if (ThrowablePrintedToSystemOutInspection.getExceptionIsPrintedToSystemOutResult(callExpression) != null) {
          return;
        }

        boolean informationLevel = InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
        if (informationLevel) {
          Collection<PsiLiteralExpression> literals =
            PsiTreeUtil.findChildrenOfType(callExpression.getArgumentList(), PsiLiteralExpression.class);
          InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(expression.getProject());
          if (!ContainerUtil.exists(literals, literal -> injectedLanguageManager.hasInjections(literal))) {
            registerError(callExpression, expression);
            return;
          }
        }
      }

      registerError(expression, expression);
    }
  }
}
