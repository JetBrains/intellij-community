/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.inferNullity;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullityInferrer {
  private static final int MAX_PASSES = 10;
  public static final String NOTHING_FOUND_TO_INFER = "Nothing found to infer";
  private int numAnnotationsAdded;
  private final List<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNotNullSet = new ArrayList<>();
  private final List<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNullableSet = new ArrayList<>();
  private final boolean myAnnotateLocalVariables;
  private final SmartPointerManager myPointerManager;


  public NullityInferrer(boolean annotateLocalVariables, Project project) {
    myAnnotateLocalVariables = annotateLocalVariables;
    myPointerManager = SmartPointerManager.getInstance(project);
  }

  private boolean expressionIsNeverNull(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final ExpressionIsNeverNullVisitor visitor = new ExpressionIsNeverNullVisitor();
    expression.accept(visitor);
    return visitor.isNeverNull();
  }

  private boolean expressionIsSometimesNull(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final ExpressionIsSometimesNullVisitor visitor = new ExpressionIsSometimesNullVisitor();
    expression.accept(visitor);
    return visitor.isSometimesNull();
  }

  private boolean methodNeverReturnsNull(@NotNull PsiMethod method) {
    final MethodNeverReturnsNullVisitor visitor = new MethodNeverReturnsNullVisitor();
    method.accept(visitor);
    return visitor.getNeverReturnsNull();
  }

  private boolean variableNeverAssignedNull(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      if (!expressionIsNeverNull(initializer)) {
        return false;
      }
    }
    else if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    final Query<PsiReference> references = ReferencesSearch.search(variable);
    for (final PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiAssignmentExpression)) {
        continue;
      }
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (assignment.getLExpression().equals(element) &&
          !expressionIsNeverNull(assignment.getRExpression())) {
        return false;
      }
    }
    return true;
  }

  private boolean variableSometimesAssignedNull(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null && expressionIsSometimesNull(initializer)) {
      return true;
    }
    final Query<PsiReference> references = ReferencesSearch.search(variable);
    for (final PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiAssignmentExpression)) {
        continue;
      }
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (assignment.getLExpression().equals(element) && expressionIsSometimesNull(assignment.getRExpression())) {
        return true;
      }
    }
    return false;
  }

  public void collect(@NotNull PsiFile file) {
    int prevNumAnnotationsAdded;
    int pass = 0;
    do {
      final NullityInferrerVisitor visitor = new NullityInferrerVisitor();
      prevNumAnnotationsAdded = numAnnotationsAdded;
      file.accept(visitor);
      pass++;
    }
    while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES);
  }

  @TestOnly
  public void apply(final Project project) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    for (SmartPsiElementPointer<? extends PsiModifierListOwner> pointer : myNullableSet) {
      annotateNullable(project, manager, pointer.getElement());
    }

    for (SmartPsiElementPointer<? extends PsiModifierListOwner> pointer : myNotNullSet) {
      annotateNotNull(project, manager, pointer.getElement());
    }

    if (myNullableSet.isEmpty() && myNotNullSet.isEmpty()) {
      throw new RuntimeException(NOTHING_FOUND_TO_INFER);
    }
  }

  public static void nothingFoundMessage(final Project project) {
    SwingUtilities.invokeLater(
      () -> Messages.showInfoMessage(project, "No places found to infer @Nullable/@NotNull", "Infer Nullity Results"));
  }

  private static void annotateNotNull(Project project,
                                      NullableNotNullManager manager,
                                      final PsiModifierListOwner element) {
    if (element != null) {
      if (element instanceof PsiField && ((PsiField)element).hasInitializer() && element.hasModifierProperty(PsiModifier.FINAL)) return;
      invoke(project, element, manager.getDefaultNotNull(), manager.getDefaultNullable());
    }
  }

  private static void annotateNullable(Project project,
                                       NullableNotNullManager manager,
                                       final PsiModifierListOwner element) {
    if (element != null) {
      invoke(project, element, manager.getDefaultNullable(), manager.getDefaultNotNull());
    }
  }

  private static void invoke(final Project project,
                             final PsiModifierListOwner element,
                             final String fqn, final String toRemove) {
    WriteCommandAction.runWriteCommandAction(project, () -> new AddAnnotationFix(fqn, element, toRemove).invoke(project, null, element.getContainingFile()));
  }

  public int getCount() {
    return myNotNullSet.size() + myNullableSet.size();
  }

  public static void apply(Project project, NullableNotNullManager manager, UsageInfo info) {
    if (info instanceof NullableUsageInfo) {
      annotateNullable(project, manager, (PsiModifierListOwner)info.getElement());
    } else if (info instanceof NotNullUsageInfo) {
      annotateNotNull(project, manager, (PsiModifierListOwner)info.getElement());
    }
  }

  private boolean shouldIgnore(PsiModifierListOwner element) {
    if (!myAnnotateLocalVariables){
      if (element instanceof PsiLocalVariable) return true;
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiForeachStatement) return true;
    }
    return false;
  }

  private void registerNullableAnnotation(@NotNull PsiModifierListOwner method) {
    registerAnnotation(method, true);
  }

  private void registerNotNullAnnotation(@NotNull PsiModifierListOwner method) {
    registerAnnotation(method, false);
  }

  private void registerAnnotation(@NotNull PsiModifierListOwner method, boolean isNullable) {
    final SmartPsiElementPointer<PsiModifierListOwner> methodPointer = myPointerManager.createSmartPsiElementPointer(method);
    if (isNullable) {
      myNullableSet.add(methodPointer);
    }
    else {
      myNotNullSet.add(methodPointer);
    }
    numAnnotationsAdded++;
  }

  private static class NullableUsageInfo extends UsageInfo {
    private NullableUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }

  private static class NotNullUsageInfo extends UsageInfo {
    private NotNullUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }

  public void collect(List<UsageInfo> usages) {
    collect(usages, true);
    collect(usages, false);
  }

  private void collect(List<UsageInfo> usages, boolean nullable) {
    final List<SmartPsiElementPointer<? extends PsiModifierListOwner>> set = nullable ? myNullableSet : myNotNullSet;
    for (SmartPsiElementPointer<? extends PsiModifierListOwner> elementPointer : set) {
      final PsiModifierListOwner element = elementPointer.getElement();
      if (element != null && !shouldIgnore(element)) {
        usages.add(nullable ? new NullableUsageInfo(element) : new NotNullUsageInfo(element));
      }
    }
  }

  private class ExpressionIsNeverNullVisitor extends JavaElementVisitor {
    private boolean neverNull = true;

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      neverNull = !"null".equals(expression.getText());
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      neverNull = expressionIsNeverNull(expression.getRExpression());
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      final PsiExpression condition = expression.getCondition();
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (canTrunkImpossibleBrunch(condition, elseExpression)) {
        neverNull = expressionIsNeverNull(thenExpression);
        return;
      }

      neverNull = expressionIsNeverNull(thenExpression) ||
                  expressionIsNeverNull(elseExpression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      neverNull = expressionIsNeverNull(expression.getExpression());
    }

    @Override
    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      neverNull = false;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      final PsiElement referent = expression.resolve();
      if (referent instanceof PsiVariable) {
        final PsiVariable var = (PsiVariable)referent;
        if (var instanceof PsiEnumConstant || isNotNull(var)) {
          neverNull = true;
          return;
        }
      }
      neverNull = false;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      neverNull = method != null && isNotNull(method);
    }

    private boolean isNeverNull() {
      return neverNull;
    }
  }

  private static boolean trunkImpossibleBrunch(PsiExpression condition,
                                               PsiExpression elseExpression,
                                               PsiExpression rOperand,
                                               PsiExpression lOperand) {
    if (rOperand instanceof PsiLiteralExpression && "null".equals(rOperand.getText()) && lOperand instanceof PsiReferenceExpression) {
      final PsiElement resolve = ((PsiReferenceExpression)lOperand).resolve();
      if (resolve instanceof PsiVariable &&
          ((PsiBinaryExpression)condition).getOperationTokenType() == JavaTokenType.EQEQ &&
          elseExpression instanceof PsiReferenceExpression &&
          ((PsiReferenceExpression)elseExpression).resolve() == resolve) {
        return true;
      }
    }
    return false;
  }

  private static boolean canTrunkImpossibleBrunch(PsiExpression condition, PsiExpression elseExpression) {
    if (condition instanceof PsiBinaryExpression) {
      final PsiExpression rOperand = ((PsiBinaryExpression)condition).getROperand();
      final PsiExpression lOperand = ((PsiBinaryExpression)condition).getLOperand();
      if (trunkImpossibleBrunch(condition, elseExpression, rOperand, lOperand) ||
          trunkImpossibleBrunch(condition, elseExpression, lOperand, rOperand)) {
        return true;
      }
    }
    return false;
  }

  private class ExpressionIsSometimesNullVisitor extends JavaRecursiveElementWalkingVisitor{
    private boolean sometimesNull;

    @Override
    public void visitElement(PsiElement element) {
      if (sometimesNull) return;
      super.visitElement(element);
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      sometimesNull = "null".equals(expression.getText());
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      sometimesNull = expressionIsSometimesNull(expression.getRExpression());
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      final PsiExpression condition = expression.getCondition();
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (canTrunkImpossibleBrunch(condition, elseExpression)) {
        sometimesNull = expressionIsSometimesNull(thenExpression);
        return;
      }

      sometimesNull = expressionIsSometimesNull(thenExpression) ||
                      expressionIsSometimesNull(elseExpression);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      final PsiElement referent = expression.resolve();
      if (referent instanceof PsiVariable) {
        final PsiVariable var = (PsiVariable)referent;
        if (isNullable(var)) {
          sometimesNull = true;
        }
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (method != null) {
        sometimesNull = isNullable(method);
      }
    }

    private boolean isSometimesNull() {
      return sometimesNull;
    }
  }

  private class MethodNeverReturnsNullVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean neverReturnsNull = true;

    @Override
    public void visitClass(PsiClass aClass) {
      //so as not to drill into anonymous classes
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {}

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression value = statement.getReturnValue();
      if (expressionIsNeverNull(value)) {
        return;
      }
      if (value instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)value;

        //if it's a recursive call, don't throw the red flag
        final PsiMethod method = methodCallExpression.resolveMethod();
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(value, PsiMethod.class);
        if (method != null && method.equals(containingMethod)) {
          return;
        }
      }
      neverReturnsNull = false;
    }

    private boolean getNeverReturnsNull() {
      return neverReturnsNull;
    }
  }

  private boolean isNotNull(PsiModifierListOwner owner) {
    if (NullableNotNullManager.isNotNull(owner)) {
      return true;
    }
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
    return myNotNullSet.contains(pointer);
  }

  private boolean isNullable(PsiModifierListOwner owner) {
    if (NullableNotNullManager.isNullable(owner)) {
      return true;
    }
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
    return myNullableSet.contains(pointer);
  }

  private class NullityInferrerVisitor extends JavaRecursiveElementWalkingVisitor{

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor() || method.getReturnType() instanceof PsiPrimitiveType) {
        return;
      }
      final Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
      for (final PsiMethod overridingMethod : overridingMethods) {
        if (isNullable(overridingMethod)) {
          registerNullableAnnotation(method);
          return;
        }
      }
      final NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
      if (!manager.isNotNull(method, false) && manager.isNotNull(method, true)) {
        registerNotNullAnnotation(method);
        return;
      }
      if (isNotNull(method) || isNullable(method)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        final boolean[] sometimesReturnsNull = new boolean[1];
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitClass(PsiClass aClass) {}

          @Override
          public void visitLambdaExpression(PsiLambdaExpression expression) {}

          @Override
          public void visitElement(PsiElement element) {
            if (sometimesReturnsNull[0]) return;
            super.visitElement(element);
          }

          @Override
          public void visitReturnStatement(PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            final PsiExpression value = statement.getReturnValue();
            if (expressionIsSometimesNull(value)) {
              sometimesReturnsNull[0] = true;
            }
          }
        });
        if (sometimesReturnsNull[0]) {
          registerNullableAnnotation(method);
          return;
        }
      }


      if (methodNeverReturnsNull(method)) {
        for (final PsiMethod overridingMethod : overridingMethods) {
          if (!isNotNull(overridingMethod)) {
            return;
          }
        }
        //and check that all of the submethods are not nullable
        registerNotNullAnnotation(method);
      }
    }


    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (variable.getType() instanceof PsiPrimitiveType ||
          isNotNull(variable) || isNullable(variable)) {
        return;
      }

      if (variableNeverAssignedNull(variable)) {
        registerNotNullAnnotation(variable);
      }
      if (variableSometimesAssignedNull(variable)) {
        registerNullableAnnotation(variable);
      }
    }


    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      if (parameter.getType() instanceof PsiPrimitiveType ||
          isNotNull(parameter) || isNullable(parameter)) {
        return;
      }
      final PsiElement grandParent = parameter.getDeclarationScope();
      if (grandParent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)grandParent;
        if (method.getBody() != null) {

          for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(method))) {
            final PsiElement place = reference.getElement();
            if (place instanceof PsiReferenceExpression) {
              final PsiReferenceExpression expr = (PsiReferenceExpression)place;
              final PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
              if (processParameter(parameter, expr, parent)) return;
              if (isNotNull(method)) {
                PsiElement toReturn = parent;
                if (parent instanceof PsiConditionalExpression &&
                    ((PsiConditionalExpression)parent).getCondition() != expr) {  //todo check conditional operations
                  toReturn = parent.getParent();
                }
                if (toReturn instanceof PsiReturnStatement) {
                  registerNotNullAnnotation(parameter);
                  return;
                }
              }
            }
          }
        }
      }
      else if (grandParent instanceof PsiForeachStatement) {
        for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(grandParent))) {
          final PsiElement place = reference.getElement();
          if (place instanceof PsiReferenceExpression) {
            final PsiReferenceExpression expr = (PsiReferenceExpression)place;
            final PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
            if (processParameter(parameter, expr, parent)) return;
          }
        }
      }
      else {
        if (variableNeverAssignedNull(parameter)) {
          registerNotNullAnnotation(parameter);
        }
        if (variableSometimesAssignedNull(parameter)) {
          registerNullableAnnotation(parameter);
        }
      }
    }

    private boolean processParameter(PsiParameter parameter, PsiReferenceExpression expr, PsiElement parent) {
      if (PsiUtil.isAccessedForWriting(expr)) return true;
      if (parent instanceof PsiBinaryExpression) {   //todo check if comparison operation
        PsiExpression opposite = null;
        final PsiExpression lOperand = ((PsiBinaryExpression)parent).getLOperand();
        final PsiExpression rOperand = ((PsiBinaryExpression)parent).getROperand();
        if (lOperand == expr) {
          opposite = rOperand;
        }
        else if (rOperand == expr) {
          opposite = lOperand;
        }
        if (opposite != null && opposite.getType() == PsiType.NULL) {
          if (parent.getParent() instanceof PsiAssertStatement &&
              ((PsiBinaryExpression)parent).getOperationTokenType() == JavaTokenType.NE) {
            registerNotNullAnnotation(parameter);
            return true;
          }
          registerNullableAnnotation(parameter);
          return true;
        }
      }
      else if (parent instanceof PsiInstanceOfExpression) {
        return true;
      }
      else if (parent instanceof PsiReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiReferenceExpression)parent).getQualifierExpression();
        if (qualifierExpression == expr) {
          registerNotNullAnnotation(parameter);
          return true;
        }
        else {
          PsiElement exprParent = expr.getParent();
          while (exprParent instanceof PsiTypeCastExpression || exprParent instanceof PsiParenthesizedExpression) {
            if (qualifierExpression == exprParent) {
              registerNotNullAnnotation(parameter);
              return true;
            }
            exprParent = exprParent.getParent();
          }
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        if (((PsiAssignmentExpression)parent).getRExpression() == expr) {
          final PsiExpression expression = ((PsiAssignmentExpression)parent).getLExpression();
          if (expression instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
            if (resolve instanceof PsiVariable) {
              final PsiVariable localVar = (PsiVariable)resolve;
              if (isNotNull(localVar)) {
                registerNotNullAnnotation(parameter);
                return true;
              }
            }
          }
        }
      } else if (parent instanceof PsiForeachStatement) {
        if (((PsiForeachStatement)parent).getIteratedValue() == expr) {
          registerNotNullAnnotation(parameter);
          return true;
        }
      }
      else if (parent instanceof PsiSwitchStatement && ((PsiSwitchStatement)parent).getExpression() == expr) {
        registerNotNullAnnotation(parameter);
        return true;
      }

      final PsiCall call = PsiTreeUtil.getParentOfType(expr, PsiCall.class);
      if (call != null) {
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          int idx = ArrayUtil.find(args, expr);
          if (idx >= 0) {
            final PsiMethod resolvedMethod = call.resolveMethod();
            if (resolvedMethod != null) {
              final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
              if (idx < parameters.length) { //not vararg
                final PsiParameter resolvedToParam = parameters[idx];
                if (isNotNull(resolvedToParam) && !resolvedToParam.isVarArgs()) {
                  registerNotNullAnnotation(parameter);
                  return true;
                }
              }
            }
          }
        }
      }
      return false;
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field instanceof PsiEnumConstant) {
        return;
      }
      if (field.getType() instanceof PsiPrimitiveType ||
          isNotNull(field) || isNullable(field)) {
        return;
      }

      if (variableNeverAssignedNull(field)) {
        registerNotNullAnnotation(field);
      }
      if (variableSometimesAssignedNull(field)) {
        registerNullableAnnotation(field);
      }
    }
  }
}
