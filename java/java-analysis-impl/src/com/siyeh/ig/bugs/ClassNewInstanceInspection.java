// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bas Leijdekkers
 */
public final class ClassNewInstanceInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.new.instance.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ClassNewInstanceFix();
  }

  private static class ClassNewInstanceFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Class.getConstructor().newInstance()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression methodExpression)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiElement parentOfType = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiTryStatement.class, PsiLambdaExpression.class);
      if (parentOfType instanceof PsiTryStatement tryStatement) {
        addCatchBlock(tryStatement, "java.lang.NoSuchMethodException", "java.lang.reflect.InvocationTargetException");
      }
      else if (parentOfType instanceof PsiLambdaExpression) {
        final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(parentOfType);
        if (FileModificationService.getInstance().preparePsiElementsForWrite(method)) {
          addThrowsClause(method, "java.lang.NoSuchMethodException",
                          "java.lang.reflect.InvocationTargetException");
        }
      }
      else if (parentOfType instanceof PsiMethod){
        final PsiMethod method = (PsiMethod)parentOfType;
        addThrowsClause(method, "java.lang.NoSuchMethodException", "java.lang.reflect.InvocationTargetException");
      }
      @NonNls final String newExpression = qualifier.getText() + ".getConstructor().newInstance()";
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression, new CommentTracker());
    }

    private static void addThrowsClause(PsiMethod method, String... exceptionNames) {
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
      final Set<String> presentExceptionNames = new HashSet<>();
      for (PsiClassType referencedType : referencedTypes) {
        final String exceptionName = referencedType.getCanonicalText();
        presentExceptionNames.add(exceptionName);
      }
      final Project project = method.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final GlobalSearchScope scope = method.getResolveScope();
      for (String exceptionName : exceptionNames) {
        if (presentExceptionNames.contains(exceptionName)) {
          continue;
        }
        final PsiJavaCodeReferenceElement throwsReference = factory.createReferenceElementByFQClassName(exceptionName, scope);
        final PsiElement element = throwsList.add(throwsReference);
        codeStyleManager.shortenClassReferences(element);
      }
    }

    protected static void addCatchBlock(PsiTryStatement tryStatement, String... exceptionNames) {
      final Project project = tryStatement.getProject();
      final PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
      final Set<PsiType> presentExceptions = Arrays.stream(parameters).map(PsiParameter::getType).collect(Collectors.toSet());
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String name = codeStyleManager.suggestUniqueVariableName("e", tryStatement.getTryBlock(), false);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (String exceptionName : exceptionNames) {
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(exceptionName, tryStatement);
        if (presentExceptions.stream().anyMatch(e -> e.isAssignableFrom(type))) {
          continue;
        }
        final PsiCatchSection section = factory.createCatchSection(type, name, tryStatement);
        final PsiCatchSection element = (PsiCatchSection)tryStatement.add(section);
        codeStyleManager.shortenClassReferences(element);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNewInstanceVisitor();
  }

  private static class ClassNewInstanceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"newInstance".equals(methodName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!CommonClassNames.JAVA_LANG_CLASS.equals(TypeUtils.resolvedClassName(qualifier.getType()))) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}