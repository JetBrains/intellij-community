// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class EnumerationCanBeIterationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  static final int KEEP_NOTHING = 0;
  static final int KEEP_INITIALIZATION = 1;
  static final int KEEP_DECLARATION = 2;
  @NonNls
  static final String ITERATOR_TEXT = "iterator()";
  @NonNls
  static final String KEY_SET_ITERATOR_TEXT = "keySet().iterator()";
  @NonNls
  static final String VALUES_ITERATOR_TEXT = "values().iterator()";

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new EnumerationCanBeIterationFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "enumeration.can.be.iteration.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EnumerationCanBeIterationVisitor();
  }

  private static class EnumerationCanBeIterationFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "enumeration.can.be.iteration.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)element.getParent();
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)methodExpression.getParent();
      final PsiElement parent =
        methodCallExpression.getParent();
      final PsiVariable variable;
      if (parent instanceof PsiVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
          return;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)target;
      }
      else {
        return;
      }
      final String variableName = createVariableName(element);
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final int result = replaceMethodCalls(variable,
                                            statement.getTextOffset(), variableName);
      final PsiType variableType = variable.getType();
      if (!(variableType instanceof PsiClassType classType)) {
        return;
      }
      final PsiType[] parameterTypes = classType.getParameters();
      final PsiType parameterType;
      if (parameterTypes.length > 0) {
        parameterType = parameterTypes[0];
      }
      else {
        parameterType = null;
      }
      final PsiStatement newStatement =
        createDeclaration(methodCallExpression, variableName,
                          parameterType);
      if (newStatement == null) {
        return;
      }
      if (parent.equals(variable)) {
        if (result == KEEP_NOTHING) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
          if (result != KEEP_INITIALIZATION) {
            final PsiExpression initializer =
              variable.getInitializer();
            if (initializer != null) {
              initializer.delete();
            }
          }
        }
      }
      else {
        if (result == KEEP_NOTHING || result == KEEP_DECLARATION) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
        }
      }
    }

    private static void insertNewStatement(PsiStatement anchor, PsiStatement newStatement) {
      final PsiElement statementParent = anchor.getParent();
      if (statementParent instanceof PsiForStatement) {
        final PsiElement statementGrandParent =
          statementParent.getParent();
        statementGrandParent.addBefore(newStatement,
                                       statementParent);
      }
      else {
        statementParent.addAfter(newStatement, anchor);
      }
    }

    @Nullable
    private static PsiStatement createDeclaration(PsiMethodCallExpression methodCallExpression,
                                                  String variableName,
                                                  PsiType parameterType) {
      @NonNls final StringBuilder newStatementText = new StringBuilder();
      final Project project = methodCallExpression.getProject();
      if (JavaCodeStyleSettings.getInstance(methodCallExpression.getContainingFile()).GENERATE_FINAL_LOCALS) {
        newStatementText.append("final ");
      }
      newStatementText.append(CommonClassNames.JAVA_UTIL_ITERATOR);
      if (parameterType != null) {
        final String typeText = parameterType.getCanonicalText();
        newStatementText.append('<');
        newStatementText.append(typeText);
        newStatementText.append('>');
      }
      newStatementText.append(' ');
      newStatementText.append(variableName);
      newStatementText.append('=');
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      final String qualifierText;
      if (qualifier == null) {
        qualifierText = "";
      }
      else {
        qualifierText = qualifier.getText() + '.';
      }
      newStatementText.append(qualifierText);
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if ("elements".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Vector")) {
          newStatementText.append(ITERATOR_TEXT);
        }
        else if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                      "java.util.Hashtable")) {
          newStatementText.append(VALUES_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else if ("keys".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Hashtable")) {
          newStatementText.append(KEY_SET_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
      newStatementText.append(';');
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement statement =
        factory.createStatementFromText(newStatementText.toString(),
                                        methodExpression);
      final JavaCodeStyleManager styleManager =
        JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(statement);
      return statement;
    }

    /**
     * @return {@link #KEEP_NOTHING} if both the variable declaration and initialization can be deleted;
     * {@link #KEEP_DECLARATION} if the initialization of the Enumeration variable can be deleted but the variable must be kept;
     * {@link #KEEP_INITIALIZATION} if the variable along with initialization should be preserved.
     */
    @MagicConstant(valuesFromClass = EnumerationCanBeIterationInspection.class)
    private static int replaceMethodCalls(PsiVariable enumerationVariable, int startOffset, String newVariableName) {
      final PsiManager manager = enumerationVariable.getManager();
      final Project project = manager.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final Query<PsiReference> query = ReferencesSearch.search(
        enumerationVariable);
      final List<PsiElement> referenceElements = new ArrayList<>();
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        referenceElements.add(referenceElement);
      }
      referenceElements.sort(PsiElementOrderComparator.getInstance());
      int result = KEEP_NOTHING;
      for (PsiElement referenceElement : referenceElements) {
        if (!(referenceElement instanceof PsiReferenceExpression referenceExpression)) {
          result = KEEP_DECLARATION;
          continue;
        }
        if (referenceElement.getTextOffset() <= startOffset) {
          result = KEEP_DECLARATION;
          continue;
        }
        final PsiElement referenceParent =
          referenceExpression.getParent();
        if (!(referenceParent instanceof PsiReferenceExpression)) {
          if (referenceParent instanceof PsiAssignmentExpression) {
            result = KEEP_DECLARATION;
            break;
          }
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiElement referenceGrandParent =
          referenceParent.getParent();
        if (!(referenceGrandParent instanceof PsiMethodCallExpression callExpression)) {
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiReferenceExpression foundReferenceExpression =
          callExpression.getMethodExpression();
        @NonNls final String foundName =
          foundReferenceExpression.getReferenceName();
        @NonNls final String newExpressionText;
        if ("hasMoreElements".equals(foundName)) {
          newExpressionText = newVariableName + ".hasNext()";
        }
        else if ("nextElement".equals(foundName)) {
          newExpressionText = newVariableName + ".next()";
        }
        else {
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiExpression newExpression =
          factory.createExpressionFromText(newExpressionText,
                                           callExpression);
        callExpression.replace(newExpression);
      }
      return result;
    }

    @NonNls
    private static String createVariableName(PsiElement context) {
      final Project project = context.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiClass iteratorClass =
        facade.findClass(CommonClassNames.JAVA_UTIL_ITERATOR, scope);
      if (iteratorClass == null) {
        return "iterator";
      }
      final JavaCodeStyleManager codeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      final PsiType iteratorType = factory.createType(iteratorClass);
      final SuggestedNameInfo baseNameInfo =
        codeStyleManager.suggestVariableName(
          VariableKind.LOCAL_VARIABLE, null, null,
          iteratorType);
      final SuggestedNameInfo nameInfo =
        codeStyleManager.suggestUniqueVariableName(baseNameInfo,
                                                   context, true);
      if (nameInfo.names.length == 0) {
        return "iterator";
      }
      return nameInfo.names[0];
    }
  }

  private static class EnumerationCanBeIterationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      final boolean isElements;
      if ("elements".equals(methodName)) {
        isElements = true;
      }
      else if ("keys".equals(methodName)) {
        isElements = false;
      }
      else {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                                                "java.util.Enumeration")) {
        return;
      }
      final PsiElement parent = expression.getParent();
      final PsiVariable variable;
      if (parent instanceof PsiLocalVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
          return;
        }
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)element;
      }
      else {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
        expression, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (!isEnumerationMethodCalled(variable, containingMethod)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (isElements) {
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Vector")) {
          registerMethodCallError(expression, ITERATOR_TEXT);
        }
        else if (InheritanceUtil.isInheritor(containingClass,
                                             "java.util.Hashtable")) {
          registerMethodCallError(expression, VALUES_ITERATOR_TEXT);
        }
      }
      else {
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Hashtable")) {
          registerMethodCallError(expression, KEY_SET_ITERATOR_TEXT);
        }
      }
    }

    private static boolean isEnumerationMethodCalled(
      @NotNull PsiVariable variable, @NotNull PsiElement context) {
      final EnumerationCanBeIterationInspection.EnumerationCanBeIterationVisitor.EnumerationMethodCalledVisitor visitor =
        new EnumerationCanBeIterationInspection.EnumerationCanBeIterationVisitor.EnumerationMethodCalledVisitor(variable);
      context.accept(visitor);
      return visitor.isEnumerationMethodCalled();
    }

    private static final class EnumerationMethodCalledVisitor
      extends JavaRecursiveElementWalkingVisitor {

      private final PsiVariable variable;
      private boolean enumerationMethodCalled;

      private EnumerationMethodCalledVisitor(@NotNull PsiVariable variable) {
        this.variable = variable;
      }

      @Override
      public void visitMethodCallExpression(
        @NotNull PsiMethodCallExpression expression) {
        if (enumerationMethodCalled) {
          return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"hasMoreElements".equals(methodName) &&
            !"nextElement".equals(methodName)) {
          return;
        }
        final PsiExpression qualifierExpression =
          methodExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression referenceExpression)) {
          return;
        }
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable variable)) {
          return;
        }
        enumerationMethodCalled = this.variable.equals(variable);
      }

      private boolean isEnumerationMethodCalled() {
        return enumerationMethodCalled;
      }
    }
  }
}