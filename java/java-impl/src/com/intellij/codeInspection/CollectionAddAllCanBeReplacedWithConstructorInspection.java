// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.performance.CollectionsListSettings;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Dmitry Batkovich
 */
public class CollectionAddAllCanBeReplacedWithConstructorInspection extends AbstractBaseJavaLocalInspectionTool {

  private final CollectionsListSettings mySettings = new CollectionsListSettings() {
    @Override
    protected Collection<String> getDefaultSettings() {
      return Collections.emptyList();
    }
  };

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    mySettings.writeSettings(node);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return mySettings.createOptionsPanel();
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySettings.readSettings(node);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement nameElement = methodExpression.getReferenceNameElement();
        final String methodName = methodExpression.getReferenceName();
        if (nameElement == null || !"addAll".equals(methodName) && !"putAll".equals(methodName)) return;
        PsiExpression[] args = expression.getArgumentList().getExpressions();
        if (args.length != 1) return;
        final PsiExpressionStatement parent = ObjectUtils.tryCast(expression.getParent(), PsiExpressionStatement.class);
        if (parent == null) return;
        PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(methodExpression.getQualifierExpression());
        if (variable == null) return;
        if (PsiUtil.resolveClassInClassTypeOnly(variable.getType()) == null) return;
        if (statementHasSubsequentAddAll(parent, variable, methodName)) return;
        PsiType argType = args[0].getType();
        if (!InheritanceUtil.isInheritor(argType, "putAll".equals(methodName) ?
                                                  CommonClassNames.JAVA_UTIL_MAP : CommonClassNames.JAVA_UTIL_COLLECTION)) {
          return;
        }

        InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(variable, parent);

        PsiNewExpression assignmentExpression;
        if (status == InitializerUsageStatus.DECLARED_JUST_BEFORE || status == InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
          if (!isCollectionConstructor(variable.getInitializer())) return;
          assignmentExpression = (PsiNewExpression)variable.getInitializer();
        }
        else {
          assignmentExpression = getPreviousAssignment(variable, parent);
        }

        if (assignmentExpression == null || !isAddAllReplaceable(expression, assignmentExpression)) return;
        final PsiMethod method = expression.resolveMethod();
        if (method != null) {
          //noinspection DialogTitleCapitalization
          holder.registerProblem(nameElement, QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.description"),
                                 new ReplaceAddAllWithConstructorFix(assignmentExpression, expression, methodName));
        }
      }
    };
  }

  private PsiNewExpression getPreviousAssignment(PsiLocalVariable variable, PsiStatement statement) {
    while (true) {
      statement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      if (statement == null) return null;
      PsiExpression expression = ExpressionUtils.getAssignmentTo(statement, variable);
      if (isCollectionConstructor(expression)) {
        return (PsiNewExpression)expression;
      }
      if (VariableAccessUtils.variableIsUsed(variable, statement)) return null;
    }
  }

  private static boolean statementHasSubsequentAddAll(@NotNull PsiElement statement,
                                                      @NotNull PsiLocalVariable referent,
                                                      @NotNull String previousMethodName) {
    final PsiElement sibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (sibling instanceof PsiExpressionStatement) {
      final PsiExpression siblingExpression = ((PsiExpressionStatement)sibling).getExpression();
      if (siblingExpression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression siblingMethodCall = (PsiMethodCallExpression)siblingExpression;
        final PsiExpression qualifier = siblingMethodCall.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression && referent.isEquivalentTo(((PsiReferenceExpression)qualifier).resolve())) {
          final PsiMethod method = siblingMethodCall.resolveMethod();
          if (method != null && method.getName().equals(previousMethodName)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean isCollectionConstructor(PsiExpression initializer) {
    if (!(initializer instanceof PsiNewExpression)) {
      return false;
    }
    final PsiNewExpression newExpression = (PsiNewExpression)initializer;
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) {
      return false;
    }
    final PsiClass initializerClass = (PsiClass)classReference.resolve();
    if (initializerClass == null) return false;
    if (!ConstructionUtils.isCollectionWithCopyConstructor(initializerClass) &&
        (!mySettings.getCollectionClassesRequiringCapacity().contains(initializerClass.getQualifiedName()) ||
         !hasProperConstructor(initializerClass))) {
      return false;
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    return argumentList != null && argumentList.isEmpty();
  }

  private static boolean hasProperConstructor(PsiClass psiClass) {
    for (PsiMethod psiMethod : psiClass.getConstructors()) {
      PsiParameterList parameterList = psiMethod.getParameterList();
      if(parameterList.getParametersCount() == 1) {
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
              InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isAddAllReplaceable(final PsiExpression addAllExpression, PsiNewExpression newExpression) {
    final boolean[] isReplaceable = new boolean[]{true};
    final PsiFile newExpressionContainingFile = newExpression.getContainingFile();
    final TextRange newExpressionTextRange = newExpression.getTextRange();

    addAllExpression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
          PsiVariable variable = (PsiVariable) resolved;
          final LocalSearchScope useScope = (LocalSearchScope)variable.getUseScope();
          if (!useScope.containsRange(newExpressionContainingFile, newExpressionTextRange)) {
            isReplaceable[0] = false;
          }
        }
      }
    });

    return isReplaceable[0];
  }

  private static class ReplaceAddAllWithConstructorFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCallExpression;
    private final SmartPsiElementPointer<PsiNewExpression> myNewExpression;
    private final String methodName;

    ReplaceAddAllWithConstructorFix(PsiNewExpression newExpression, PsiMethodCallExpression expression, String methodName) {
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(newExpression.getProject());
      myMethodCallExpression = smartPointerManager.createSmartPsiElementPointer(expression);
      myNewExpression = smartPointerManager.createSmartPsiElementPointer(newExpression);
      this.methodName = methodName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.name", methodName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCallExpression = myMethodCallExpression.getElement();
      if (methodCallExpression == null) return;
      PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(methodCallExpression.getParent(), PsiExpressionStatement.class);
      if (expressionStatement == null) return;
      final PsiNewExpression newExpression = myNewExpression.getElement();
      if (newExpression == null) return;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(newExpression.getParent());
      PsiVariable variable = null;
      if (parent instanceof PsiVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        variable = ExpressionUtils.resolveLocalVariable(((PsiAssignmentExpression)parent).getLExpression());
      }
      if (variable == null) return;
      PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference == null) return;

      CommentTracker ct = new CommentTracker();
      final PsiElement parameter = methodCallExpression.getArgumentList().getExpressions()[0];
      String replacement = "new " + reference.getText() + "(" + ct.text(parameter) + ")";
      if (parent instanceof PsiAssignmentExpression) {
        ct.delete(parent);
      }
      else {
        if (variable.getParent() instanceof PsiDeclarationStatement &&
            ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length == 1) {
          PsiElement scope = PsiTreeUtil.getParentOfType(expressionStatement, PsiMember.class, PsiStatement.class, PsiLambdaExpression.class);
          if (scope != null &&
              ReferencesSearch.search(variable).forEach((PsiReference ref) -> PsiTreeUtil.isAncestor(scope, ref.getElement(), true))) {
            PsiDeclarationStatement newDeclaration =
              JavaPsiFacade.getElementFactory(project).createVariableDeclarationStatement("x", PsiType.INT, null, methodCallExpression);
            PsiVariable newVariable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(variable);
            ct.delete(variable);
            ct.replace(Objects.requireNonNull(newVariable.getInitializer()), replacement);
            ct.replaceAndRestoreComments(expressionStatement, newDeclaration);
            return;
          }
        }
        ct.delete(newExpression);
      }
      ct.replaceAndRestoreComments(methodCallExpression, variable.getName() + "=" + replacement);
    }
  }
}