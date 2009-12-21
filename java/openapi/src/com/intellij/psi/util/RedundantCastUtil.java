/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 24, 2002
 * Time: 6:08:14 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RedundantCastUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastUtil");

  @NotNull
  public static List<PsiTypeCastExpression> getRedundantCastsInside(PsiElement where) {
    MyCollectingVisitor visitor = new MyCollectingVisitor();
    where.acceptChildren(visitor);
    return new ArrayList<PsiTypeCastExpression>(visitor.myFoundCasts);
  }

  public static boolean isCastRedundant (PsiTypeCastExpression typeCast) {
    PsiElement parent = typeCast.getParent();
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
    if (parent instanceof PsiExpressionList) parent = parent.getParent();
    if (parent instanceof PsiReferenceExpression) parent = parent.getParent();
    MyIsRedundantVisitor visitor = new MyIsRedundantVisitor(false);
    parent.accept(visitor);
    return visitor.isRedundant;
  }

  @Nullable
  private static PsiExpression deparenthesizeExpression(PsiExpression arg) {
    while (arg instanceof PsiParenthesizedExpression) arg = ((PsiParenthesizedExpression) arg).getExpression();
    return arg;
  }

  private static class MyCollectingVisitor extends MyIsRedundantVisitor {
    private final Set<PsiTypeCastExpression> myFoundCasts = new HashSet<PsiTypeCastExpression>();

    private MyCollectingVisitor() {
      super(true);
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      expression.acceptChildren(this);
    }

    @Override public void visitClass(PsiClass aClass) {
      // avoid multiple visit
    }

    @Override public void visitMethod(PsiMethod method) {
      // avoid multiple visit
    }

    @Override public void visitField(PsiField field) {
      // avoid multiple visit
    }

    protected void addToResults(@NotNull PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantical(typeCast)) {
        myFoundCasts.add(typeCast);
      }
    }
  }

  private static class MyIsRedundantVisitor extends JavaRecursiveElementVisitor {
    private boolean isRedundant = false;
    private final boolean myRecursive;

    private MyIsRedundantVisitor(final boolean recursive) {
      myRecursive = recursive;
    }

    @Override
    public void visitElement(final PsiElement element) {
      if (myRecursive) {
        super.visitElement(element);
      }
    }

    protected void addToResults(@NotNull PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantical(typeCast)) {
        isRedundant = true;
      }
    }

    @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      processPossibleTypeCast(expression.getRExpression(), expression.getLExpression().getType());
      super.visitAssignmentExpression(expression);
    }

    @Override public void visitVariable(PsiVariable variable) {
      processPossibleTypeCast(variable.getInitializer(), variable.getType());
      super.visitVariable(variable);
    }

    @Override public void visitReturnStatement(PsiReturnStatement statement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      if (method != null) {
        final PsiType returnType = method.getReturnType();
        final PsiExpression returnValue = statement.getReturnValue();
        if (returnValue != null) {
          processPossibleTypeCast(returnValue, returnType);
        }
      }
      super.visitReturnStatement(statement);
    }

    @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
      PsiExpression rExpr = deparenthesizeExpression(expression.getLOperand());
      PsiExpression lExpr = deparenthesizeExpression(expression.getROperand());

      if (rExpr != null && lExpr != null) {
        final IElementType binaryToken = expression.getOperationSign().getTokenType();
        processBinaryExpressionOperand(lExpr, rExpr, binaryToken);
        processBinaryExpressionOperand(rExpr, lExpr, binaryToken);
      }
      super.visitBinaryExpression(expression);
    }

    private void processBinaryExpressionOperand(final PsiExpression operand,
                                                final PsiExpression otherOperand,
                                                final IElementType binaryToken) {
      if (operand instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression toCast = typeCast.getOperand();
        if (toCast != null && TypeConversionUtil.isBinaryOperatorApplicable(binaryToken, toCast, otherOperand, false)) {
          addToResults(typeCast);
        }
      }
    }

    private void processPossibleTypeCast(PsiExpression rExpr, @Nullable PsiType lType) {
      rExpr = deparenthesizeExpression(rExpr);
      if (rExpr instanceof PsiTypeCastExpression) {
        PsiExpression castOperand = ((PsiTypeCastExpression)rExpr).getOperand();
        if (castOperand != null) {
          PsiType operandType = castOperand.getType();
          if (operandType != null) {
            if (lType != null && TypeConversionUtil.isAssignable(lType, operandType, false)) {
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      processCall(expression);

      checkForVirtual(expression);
      super.visitMethodCallExpression(expression);
    }

    private void checkForVirtual(PsiMethodCallExpression methodCall) {
      PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
      PsiExpression qualifier = methodExpr.getQualifierExpression();
      if (!(qualifier instanceof PsiParenthesizedExpression)) return;
      PsiExpression operand = ((PsiParenthesizedExpression)qualifier).getExpression();
      if (!(operand instanceof PsiTypeCastExpression)) return;
      PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
      PsiExpression castOperand = typeCast.getOperand();
      if (castOperand == null) return;

      PsiType type = castOperand.getType();
      if (type == null) return;
      if (type instanceof PsiPrimitiveType) return;

      final JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
      PsiMethod targetMethod = (PsiMethod)resolveResult.getElement();
      if (targetMethod == null) return;
      if (targetMethod.hasModifierProperty(PsiModifier.STATIC)) return;

      try {
        PsiManager manager = methodExpr.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

        PsiMethodCallExpression newCall = (PsiMethodCallExpression)factory.createExpressionFromText(methodCall.getText(), methodCall);
        PsiExpression newQualifier = newCall.getMethodExpression().getQualifierExpression();
        PsiExpression newOperand = ((PsiTypeCastExpression)((PsiParenthesizedExpression)newQualifier).getExpression()).getOperand();
        newQualifier.replace(newOperand);

        final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
        if (!newResult.isValidResult()) return;
        final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
        final PsiType newReturnType = newResult.getSubstitutor().substitute(newTargetMethod.getReturnType());
        final PsiType oldReturnType = resolveResult.getSubstitutor().substitute(targetMethod.getReturnType());
        if (Comparing.equal(newReturnType, oldReturnType)) {
          if (newTargetMethod.equals(targetMethod)) {
            addToResults(typeCast);
          }
          else if (
            newTargetMethod.getSignature(newResult.getSubstitutor()).equals(targetMethod.getSignature(resolveResult.getSubstitutor())) &&
            !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated()) &&  // see SCR11555, SCR14559
            areThrownExceptionsCompatible(targetMethod, newTargetMethod)) { //see IDEADEV-15170
            addToResults(typeCast);
          }
        }
        qualifier = ((PsiTypeCastExpression)((PsiParenthesizedExpression)qualifier).getExpression()).getOperand();
      }
      catch (IncorrectOperationException e) {
      }
    }

    private static boolean areThrownExceptionsCompatible(final PsiMethod targetMethod, final PsiMethod newTargetMethod) {
      final PsiClassType[] oldThrowsTypes = targetMethod.getThrowsList().getReferencedTypes();
      final PsiClassType[] newThrowsTypes = newTargetMethod.getThrowsList().getReferencedTypes();
      for (final PsiClassType throwsType : newThrowsTypes) {
        if (!isExceptionThrown(throwsType, oldThrowsTypes)) return false;
      }
      return true;
    }

    private static boolean isExceptionThrown(PsiClassType exceptionType, PsiClassType[] thrownTypes) {
      for (final PsiClassType type : thrownTypes) {
        if (type.equals(exceptionType)) return true;
      }
      return false;
    }

    @Override public void visitNewExpression(PsiNewExpression expression) {
      processCall(expression);
      super.visitNewExpression(expression);
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      //expression.acceptChildren(this);
    }

    private void processCall(PsiCallExpression expression){
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return;
      PsiExpression[] args = argumentList.getExpressions();
      PsiMethod oldMethod = expression.resolveMethod();
      if (oldMethod == null) return;
      PsiParameter[] parameters = oldMethod.getParameterList().getParameters();

      try {
        for (int i = 0; i < args.length; i++) {
          final PsiExpression arg = deparenthesizeExpression(args[i]);
          if (arg instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = (PsiTypeCastExpression)arg;
            if (i == args.length - 1 && args.length == parameters.length && parameters[i].isVarArgs()) {
              //do not mark cast to resolve ambiguity for calling varargs method with inexact argument
              continue;
            }
            PsiCallExpression newCall = (PsiCallExpression) expression.copy();
            final PsiExpressionList argList = newCall.getArgumentList();
            LOG.assertTrue(argList != null);
            PsiExpression[] newArgs = argList.getExpressions();
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) deparenthesizeExpression(newArgs[i]);
            PsiExpression castOperand = castExpression.getOperand();
            if (castOperand == null) return;
            castExpression.replace(castOperand);
            final JavaResolveResult newResult = newCall.resolveMethodGenerics();
            if (oldMethod.equals(newResult.getElement()) && newResult.isValidResult() &&
                Comparing.equal(newCall.getType(), expression.getType())) {
              addToResults(cast);
            }
          }
        }
      }
      catch (IncorrectOperationException e) {
        return;
      }

      for (PsiExpression arg : args) {
        if (arg instanceof PsiTypeCastExpression) {
          PsiExpression castOperand = ((PsiTypeCastExpression)arg).getOperand();
          if (castOperand != null) {
            castOperand.accept(this);
          }
        }
        else {
          arg.accept(this);
        }
      }
    }

    @Override public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
      PsiExpression operand = typeCast.getOperand();
      if (operand == null) return;

      PsiElement expr = deparenthesizeExpression(operand);

      if (expr instanceof PsiTypeCastExpression) {
        PsiTypeElement typeElement = ((PsiTypeCastExpression)expr).getCastType();
        if (typeElement == null) return;
        PsiType castType = typeElement.getType();
        if (!(castType instanceof PsiPrimitiveType)) {
          addToResults((PsiTypeCastExpression)expr);
        }
      }
      else {
        PsiElement parent = typeCast.getParent();
        if (parent instanceof PsiConditionalExpression) {
          if (!PsiUtil.isLanguageLevel5OrHigher(typeCast)) {
            //branches need to be of the same type
            if (!Comparing.equal(operand.getType(), ((PsiConditionalExpression)parent).getType())) return;
          }
        }
        processAlreadyHasTypeCast(typeCast);
      }
    }

    private void processAlreadyHasTypeCast(PsiTypeCastExpression typeCast){
      PsiElement parent = typeCast.getParent();
      while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
      if (parent instanceof PsiExpressionList) return; // do not replace in arg lists - should be handled by parent

      if (isTypeCastSemantical(typeCast)) return;

      PsiTypeElement typeElement = typeCast.getCastType();
      if (typeElement == null) return;
      PsiType castTo = typeElement.getType();
      PsiType opType = typeCast.getOperand().getType();
      if (opType == null) return;
      if (parent instanceof PsiReferenceExpression) {
        if (castTo instanceof PsiClassType && opType instanceof PsiPrimitiveType) return; //explicit boxing
        //Check accessibility
        if (opType instanceof PsiClassType) {
          final PsiReferenceExpression refExpression = (PsiReferenceExpression)parent;
          PsiElement element = refExpression.resolve();
          if (!(element instanceof PsiMember)) return;
          PsiClass accessClass = ((PsiClassType)opType).resolve();
          if (accessClass == null) return;
          if (!JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper().isAccessible((PsiMember)element, typeCast, accessClass)) return;
          if (!isCastRedundantInRefExpression(refExpression, typeCast.getOperand())) return;
        }
      }

      if (someWhereAtTheLeftSideOfAssignment(parent)) {
        if (TypeConversionUtil.isAssignable(opType, castTo, false)) {
          addToResults(typeCast);
        }
      }
      else {
        if (TypeConversionUtil.isAssignable(castTo, opType, false)) {
          addToResults(typeCast);
        }
      }
    }

    private static boolean someWhereAtTheLeftSideOfAssignment(PsiElement element) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, false, PsiMember.class);
      if (assignment == null) return false;
      PsiExpression lExpression = assignment.getLExpression();
      return PsiTreeUtil.isAncestor(lExpression, element, false);
    }
  }

  private static boolean isCastRedundantInRefExpression (final PsiReferenceExpression refExpression, final PsiExpression castOperand) {
    final PsiElement resolved = refExpression.resolve();
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    refExpression.getManager().performActionWithFormatterDisabled(new Runnable() {
      public void run() {
        try {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(refExpression.getProject()).getElementFactory();
          final PsiExpression copyExpression = elementFactory.createExpressionFromText(refExpression.getText(), refExpression);
          if (copyExpression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression copy = (PsiReferenceExpression)copyExpression;
            final PsiExpression qualifier = copy.getQualifierExpression();
            if (qualifier != null) {
              qualifier.replace(castOperand);
              result.set(Boolean.valueOf(copy.resolve() == resolved));
            }
          }
        }
        catch (IncorrectOperationException e) {
        }
      }
    });
    return result.get().booleanValue();
  }

  public static boolean isTypeCastSemantical(PsiTypeCastExpression typeCast) {
    PsiExpression operand = typeCast.getOperand();
    if (operand == null) return false;
    PsiType opType = operand.getType();
    PsiTypeElement typeElement = typeCast.getCastType();
    if (typeElement == null) return false;
    PsiType castType = typeElement.getType();
    if (castType instanceof PsiPrimitiveType) {
      if (opType instanceof PsiPrimitiveType) {
        return !opType.equals(castType); // let's suppose all not equal primitive casts are necessary
      }
      final PsiPrimitiveType unboxedOpType = PsiPrimitiveType.getUnboxedType(opType);
      if (unboxedOpType != null && !unboxedOpType.equals(castType) ) {
        return true;
      }
    }
    else if (castType instanceof PsiClassType && ((PsiClassType)castType).hasParameters()) {
      if (opType instanceof PsiClassType && ((PsiClassType)opType).isRaw()) return true;
    }

    PsiElement parent = typeCast.getParent();
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();

    if (parent instanceof PsiBinaryExpression) {
      PsiBinaryExpression expression = (PsiBinaryExpression)parent;
      PsiExpression firstOperand = expression.getLOperand();
      PsiExpression otherOperand = expression.getROperand();
      if (PsiTreeUtil.isAncestor(otherOperand, typeCast, false)) {
        PsiExpression temp = otherOperand;
        otherOperand = firstOperand;
        firstOperand = temp;
      }
      if (firstOperand != null && otherOperand != null && wrapperCastChangeSemantics(firstOperand, otherOperand, operand)) {
        return true;
      }
    }
    return false;
  }
  private static boolean wrapperCastChangeSemantics(PsiExpression operand, PsiExpression otherOperand, PsiExpression toCast) {
    boolean isPrimitiveComparisonWithCast = TypeConversionUtil.isPrimitiveAndNotNull(operand.getType()) || TypeConversionUtil.isPrimitiveAndNotNull(otherOperand.getType());
    boolean isPrimitiveComparisonWithoutCast = TypeConversionUtil.isPrimitiveAndNotNull(toCast.getType()) || TypeConversionUtil.isPrimitiveAndNotNull(otherOperand.getType());
    // wrapper casted to primitive vs wrapper comparison

    return isPrimitiveComparisonWithCast != isPrimitiveComparisonWithoutCast;
  }
}
