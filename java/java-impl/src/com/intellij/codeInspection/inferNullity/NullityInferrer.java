/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class NullityInferrer {
  private static final Logger LOG = Logger.getInstance("#" + NullityInferrer.class.getName());

  private static final int MAX_PASSES = 10;
  private int numAnnotationsAdded = 0;
  private final HashSet<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNotNullSet =
    new HashSet<SmartPsiElementPointer<? extends PsiModifierListOwner>>();
  private final HashSet<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNullableSet =
    new HashSet<SmartPsiElementPointer<? extends PsiModifierListOwner>>();
  private boolean myAnnotateLocalVariables;
  private SmartPointerManager myPointerManager;


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

  protected boolean expressionIsSometimesNull(@Nullable PsiExpression expression) {
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

  public void apply(Project project) {
    for (SmartPsiElementPointer<? extends PsiModifierListOwner> pointer : myNullableSet) {
      final PsiModifierListOwner element = pointer.getElement();
      if (element != null) {
        if (!myAnnotateLocalVariables && element instanceof PsiLocalVariable) continue;
        new AddAnnotationFix(AnnotationUtil.NULLABLE, element, AnnotationUtil.NOT_NULL).invoke(project, null, element.getContainingFile());
      }
    }

    for (SmartPsiElementPointer<? extends PsiModifierListOwner> pointer : myNotNullSet) {
      final PsiModifierListOwner element = pointer.getElement();
      if (element != null) {
        if (!myAnnotateLocalVariables && element instanceof PsiLocalVariable) continue;
        new AddAnnotationFix(AnnotationUtil.NOT_NULL, element, AnnotationUtil.NULLABLE).invoke(project, null, element.getContainingFile());
      }
    }
  }

  private void registerNullableAnnotation(@NotNull PsiModifierListOwner method) {
    registerAnnotation(method, true);
  }

  private void registerNotNullAnnotation(@NotNull PsiModifierListOwner method) {
    registerAnnotation(method, false);
  }

  private void registerAnnotation(@NotNull PsiModifierListOwner method, boolean isNullable) {
    final SmartPsiElementPointer<PsiModifierListOwner> methodPointer = myPointerManager.createLazyPointer(method);
    if (isNullable) {
      myNullableSet.add(methodPointer);
    }
    else {
      myNotNullSet.add(methodPointer);
    }
    numAnnotationsAdded++;
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
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      neverNull = expressionIsNeverNull(expression.getThenExpression()) &&
                  expressionIsNeverNull(expression.getElseExpression());
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
      if (method == null) {
        neverNull = false;
      }
      neverNull = isNotNull(method);
    }

    private boolean isNeverNull() {
      return neverNull;
    }
  }

  private class ExpressionIsSometimesNullVisitor extends JavaRecursiveElementWalkingVisitor{
    private boolean sometimesNull = false;

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
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      sometimesNull = expressionIsSometimesNull(expression.getThenExpression()) ||
                      expressionIsSometimesNull(expression.getElseExpression());
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
    if (AnnotationUtil.isNotNull(owner)) {
      return true;
    }
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createLazyPointer(owner);
    return myNotNullSet.contains(pointer);
  }

  private boolean isNullable(PsiModifierListOwner owner) {
    if (AnnotationUtil.isNullable(owner)) {
      return true;
    }
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createLazyPointer(owner);
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
      if (!AnnotationUtil.isAnnotated(method, AnnotationUtil.NOT_NULL, false) &&
          AnnotationUtil.isAnnotated(method, AnnotationUtil.NOT_NULL, true)) {
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
          public void visitClass(PsiClass aClass) {
          }

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
      final PsiElement grandParent = parameter.getParent().getParent();
      if (grandParent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)grandParent;
        if (method.getBody() != null) {

          for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(method))) {
            final PsiElement place = reference.getElement();
            if (place instanceof PsiReferenceExpression) {
              final PsiReferenceExpression expr = (PsiReferenceExpression)place;
              if (PsiUtil.isAccessedForWriting(expr)) return;
              final PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
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
                  registerNullableAnnotation(parameter);
                  return;
                }
              }
              else if (parent instanceof PsiReferenceExpression) {
                if (((PsiReferenceExpression)parent).getQualifierExpression() == expr) {
                  registerNotNullAnnotation(parameter);
                  return;
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
                        return;
                      }
                    }
                  }
                }
              }

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
                          return;
                        }
                      }
                    }
                  }
                }
              }
            }
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
