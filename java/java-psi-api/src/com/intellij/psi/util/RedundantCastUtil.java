/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 * Date: Mar 24, 2002
 */
public class RedundantCastUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastUtil");

  private RedundantCastUtil() { }

  @NotNull
  public static List<PsiTypeCastExpression> getRedundantCastsInside(PsiElement where) {
    MyCollectingVisitor visitor = new MyCollectingVisitor();
    if (where instanceof PsiEnumConstant) {
      where.accept(visitor);
    } else {
      where.acceptChildren(visitor);
    }
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

  public static void removeCast(PsiTypeCastExpression castExpression) {
    if (castExpression == null) return;
    PsiExpression operand = castExpression.getOperand();
    if (operand instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression)operand;
      operand = parExpr.getExpression();
    }
    if (operand == null) return;

    PsiElement toBeReplaced = castExpression;

    PsiElement parent = castExpression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      toBeReplaced = parent;
      parent = parent.getParent();
    }

    try {
      toBeReplaced.replace(operand);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
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

    @Override
    protected void addToResults(@NotNull PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantic(typeCast)) {
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
      if (!isTypeCastSemantic(typeCast)) {
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

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      IElementType tokenType = expression.getOperationTokenType();
      PsiExpression[] operands = expression.getOperands();
      if (operands.length >= 2) {
        PsiType lType = operands[0].getType();
        processBinaryExpressionOperand(deparenthesizeExpression(operands[0]), operands[1].getType(), tokenType);
        for (int i = 1; i < operands.length; i++) {
          PsiExpression operand = deparenthesizeExpression(operands[i]);
          if (operand == null) continue;
          processBinaryExpressionOperand(operand, lType, tokenType);
          lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, operand.getType(), tokenType, true);
        }
      }
      super.visitPolyadicExpression(expression);
    }

    private void processBinaryExpressionOperand(final PsiExpression operand,
                                                final PsiType otherType,
                                                final IElementType binaryToken) {
      if (operand instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression toCast = typeCast.getOperand();
        if (toCast != null && TypeConversionUtil.isBinaryOperatorApplicable(binaryToken, toCast.getType(), otherType, false)) {
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

        final PsiExpression expressionFromText = factory.createExpressionFromText(methodCall.getText(), methodCall);
        if (!(expressionFromText instanceof PsiMethodCallExpression)) return;
        PsiMethodCallExpression newCall = (PsiMethodCallExpression)expressionFromText;
        PsiExpression newQualifier = newCall.getMethodExpression().getQualifierExpression();
        PsiExpression newOperand = ((PsiTypeCastExpression)((PsiParenthesizedExpression)newQualifier).getExpression()).getOperand();
        newQualifier.replace(newOperand);

        final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
        if (!newResult.isValidResult()) return;
        final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
        final PsiType newReturnType = newCall.getType();
        final PsiType oldReturnType = methodCall.getType();
        if (Comparing.equal(newReturnType, oldReturnType)) {
          if (newTargetMethod.equals(targetMethod) ||
              (newTargetMethod.getSignature(newResult.getSubstitutor()).equals(targetMethod.getSignature(resolveResult.getSubstitutor())) &&
               !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated()) &&  // see SCR11555, SCR14559
               areThrownExceptionsCompatible(targetMethod, newTargetMethod))) {
            addToResults(typeCast);
          }
        }
      }
      catch (IncorrectOperationException ignore) { }
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

    @Override
    public void visitEnumConstant(PsiEnumConstant enumConstant) {
      processCall(enumConstant);
      super.visitEnumConstant(enumConstant);
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      //expression.acceptChildren(this);
    }

    private void processCall(PsiCall expression){
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
            PsiCall newCall = (PsiCall) expression.copy();
            final PsiExpressionList argList = newCall.getArgumentList();
            LOG.assertTrue(argList != null);
            PsiExpression[] newArgs = argList.getExpressions();
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) deparenthesizeExpression(newArgs[i]);
            PsiExpression castOperand = castExpression.getOperand();
            if (castOperand == null) return;
            castExpression.replace(castOperand);
            if (newCall instanceof PsiEnumConstant) {
              // do this manually, because PsiEnumConstantImpl.resolveMethodGenerics() will assert (no containing class for the copy)
              final PsiEnumConstant enumConstant = (PsiEnumConstant)expression;
              PsiClass containingClass = enumConstant.getContainingClass();
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(enumConstant.getProject());
              final PsiClassType type = facade.getElementFactory().createType(containingClass);
              final JavaResolveResult newResult = facade.getResolveHelper().resolveConstructor(type, newCall.getArgumentList(), enumConstant);
              if (oldMethod.equals(newResult.getElement()) && newResult.isValidResult()) {
                addToResults(cast);
              }
            } else {
              final JavaResolveResult newResult = newCall.resolveMethodGenerics();
              if (oldMethod.equals(newResult.getElement()) && newResult.isValidResult() &&
                Comparing.equal(((PsiCallExpression)newCall).getType(), ((PsiCallExpression)expression).getType())) {
                addToResults(cast);
              }
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

      final PsiType topCastType = typeCast.getType();
      if (expr instanceof PsiTypeCastExpression) {
        PsiTypeElement typeElement = ((PsiTypeCastExpression)expr).getCastType();
        if (typeElement == null) return;
        PsiType castType = typeElement.getType();
        final PsiExpression innerOperand = ((PsiTypeCastExpression)expr).getOperand();
        final PsiType operandType = innerOperand != null ? innerOperand.getType() : null;
        if (!(castType instanceof PsiPrimitiveType)) {
          if (operandType != null && topCastType != null && TypeConversionUtil.areTypesConvertible(operandType, topCastType)) {
            addToResults((PsiTypeCastExpression)expr);
          }
        } else if (PsiPrimitiveType.getUnboxedType(operandType) == topCastType) {
          addToResults((PsiTypeCastExpression)expr);
        }
      }
      else {
        PsiElement parent = typeCast.getParent();
        if (parent instanceof PsiConditionalExpression) {
          //branches need to be of the same type
          final PsiType operandType = operand.getType();
          final PsiType conditionalType = ((PsiConditionalExpression)parent).getType();
          if (!Comparing.equal(operandType, conditionalType)) {
            if (!PsiUtil.isLanguageLevel5OrHigher(typeCast)) {
              return;
            }
            if (!checkResolveAfterRemoveCast(parent)) return;
            final PsiExpression thenExpression = ((PsiConditionalExpression)parent).getThenExpression();
            final PsiExpression elseExpression = ((PsiConditionalExpression)parent).getElseExpression();
            final PsiExpression opposite = thenExpression == typeCast ? elseExpression : thenExpression;
            if (opposite == null || !Comparing.equal(conditionalType, opposite.getType())) return;
          }
        } else if (parent instanceof PsiSynchronizedStatement && (expr instanceof PsiExpression && ((PsiExpression)expr).getType() instanceof PsiPrimitiveType)) {
          return;
        } else if (expr instanceof PsiLambdaExpression || expr instanceof PsiMethodReferenceExpression) {
          if (parent instanceof PsiParenthesizedExpression && parent.getParent() instanceof PsiReferenceExpression) {
            return;
          }

          final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(typeCast, true);
          if (topCastType != null && functionalInterfaceType != null && !TypeConversionUtil.isAssignable(topCastType, functionalInterfaceType, false)) return;
        }
        processAlreadyHasTypeCast(typeCast);
      }
      super.visitTypeCastExpression(typeCast);
    }

    private static boolean checkResolveAfterRemoveCast(PsiElement parent) {
      PsiElement grandPa = parent.getParent();
      if (grandPa instanceof PsiExpressionList) {
        PsiExpression[] expressions = ((PsiExpressionList)grandPa).getExpressions();
        int idx = ArrayUtil.find(expressions, parent);
        PsiElement grandGrandPa = grandPa.getParent();
        if (grandGrandPa instanceof PsiCall) {
          PsiElement resolve = ((PsiCall)grandGrandPa).resolveMethod();
          if (resolve instanceof PsiMethod) {
            PsiCall expression = (PsiCall)grandGrandPa.copy();
            PsiExpressionList argumentList = expression.getArgumentList();
            LOG.assertTrue(argumentList != null);
            PsiExpression toReplace = argumentList.getExpressions()[idx];
            if (toReplace instanceof PsiConditionalExpression) {
              PsiExpression thenExpression = ((PsiConditionalExpression)toReplace).getThenExpression();
              PsiExpression elseExpression = ((PsiConditionalExpression)toReplace).getElseExpression();
              if (thenExpression instanceof PsiTypeCastExpression) {
                final PsiExpression thenOperand = ((PsiTypeCastExpression)thenExpression).getOperand();
                if (thenOperand != null) {
                  thenExpression.replace(thenOperand);
                }
              } else if (elseExpression instanceof PsiTypeCastExpression) {
                final PsiExpression elseOperand = ((PsiTypeCastExpression)elseExpression).getOperand();
                if (elseOperand != null) {
                  elseExpression.replace(elseOperand);
                }
              }
            }
            if (expression.resolveMethod() != resolve) {
              return false;
            }
          }
        }
      }
      return true;
    }

    private void processAlreadyHasTypeCast(PsiTypeCastExpression typeCast){
      PsiElement parent = typeCast.getParent();
      while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
      if (parent instanceof PsiExpressionList) return; // do not replace in arg lists - should be handled by parent
      if (parent instanceof PsiReturnStatement) return;
      if (parent instanceof PsiTypeCastExpression) return;

      if (isTypeCastSemantic(typeCast)) return;

      PsiTypeElement typeElement = typeCast.getCastType();
      if (typeElement == null) return;
      final PsiType castTo = typeElement.getType();
      final PsiExpression operand = typeCast.getOperand();

      PsiType opType = operand.getType();
      final PsiType expectedTypeByParent = PsiTypesUtil.getExpectedTypeByParent(typeCast);
      if (expectedTypeByParent != null) {
        try {
          final Project project = operand.getProject();
          final String uniqueVariableName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("l", parent, false);
          final PsiDeclarationStatement declarationStatement =
            (PsiDeclarationStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(
              expectedTypeByParent.getCanonicalText() + " " + uniqueVariableName + " = " + operand.getText() + ";", parent);
          final PsiExpression initializer = ((PsiLocalVariable)declarationStatement.getDeclaredElements()[0]).getInitializer();
          LOG.assertTrue(initializer != null, operand.getText());
          opType = initializer.getType();
        }
        catch (IncorrectOperationException ignore) {}
      }

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
          if (!isCastRedundantInRefExpression(refExpression, operand)) return;
        }
      }

      if (arrayAccessAtTheLeftSideOfAssignment(parent)) {
        if (TypeConversionUtil.isAssignable(opType, castTo, false) && opType.getArrayDimensions() == castTo.getArrayDimensions()) {
          addToResults(typeCast);
        }
      }
      else {
        if (parent instanceof PsiInstanceOfExpression && opType instanceof PsiPrimitiveType) {
          return;
        }
        if (parent instanceof PsiForeachStatement) {
          if (InheritanceUtil.isInheritor(PsiUtil.resolveClassInType(opType), false, CommonClassNames.JAVA_LANG_ITERABLE)) {
            addToResults(typeCast);
            return;
          }
        }
        if (parent instanceof PsiThrowStatement) {
          final PsiClass thrownClass = PsiUtil.resolveClassInType(opType);
          if (InheritanceUtil.isInheritor(thrownClass, false, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
            addToResults(typeCast);
            return;
          }
          if (InheritanceUtil.isInheritor(thrownClass, false, CommonClassNames.JAVA_LANG_THROWABLE)) {
            final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
            if (method != null) {
              for (PsiClassType thrownType : method.getThrowsList().getReferencedTypes()) {
                if (TypeConversionUtil.isAssignable(thrownType, opType, false)) {
                  addToResults(typeCast);
                  return;
                }
              }
            }
          }
        }
        if (parent instanceof PsiInstanceOfExpression || (TypeConversionUtil.isAssignable(castTo, opType, false) && 
                                                          (expectedTypeByParent == null || TypeConversionUtil.isAssignable(expectedTypeByParent, opType, false)))) {
          addToResults(typeCast);
        }
      }
    }

    private static boolean arrayAccessAtTheLeftSideOfAssignment(PsiElement element) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, false, PsiMember.class);
      if (assignment == null) return false;
      PsiExpression lExpression = assignment.getLExpression();
      return PsiTreeUtil.isAncestor(lExpression, element, false) && lExpression instanceof PsiArrayAccessExpression;
    }
  }

  private static boolean isCastRedundantInRefExpression (final PsiReferenceExpression refExpression, final PsiExpression castOperand) {
    final PsiElement resolved = refExpression.resolve();
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    CodeStyleManager.getInstance(refExpression.getProject()).performActionWithFormatterDisabled(new Runnable() {
      @Override
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
        catch (IncorrectOperationException ignore) { }
      }
    });
    return result.get().booleanValue();
  }

  /** @deprecated use {@link #isTypeCastSemantic(PsiTypeCastExpression)} (to remove in IDEA 14) */
  @SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection"})
  public static boolean isTypeCastSemantical(PsiTypeCastExpression typeCast) {
    return isTypeCastSemantic(typeCast);
  }

  public static boolean isTypeCastSemantic(PsiTypeCastExpression typeCast) {
    PsiExpression operand = typeCast.getOperand();
    if (operand == null) return false;

    if (isInPolymorphicCall(typeCast)) return true;

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
    } else if (castType instanceof PsiClassType && ((PsiClassType)castType).isRaw()) {
      if (opType instanceof PsiClassType && ((PsiClassType)opType).hasParameters()) return true;
    }

    if (operand instanceof PsiLambdaExpression || operand instanceof PsiMethodReferenceExpression) {
      if (castType instanceof PsiClassType && InheritanceUtil.isInheritor(PsiUtil.resolveClassInType(castType), CommonClassNames.JAVA_IO_SERIALIZABLE)) return true;
      if (castType instanceof PsiIntersectionType) {
        boolean redundant = false;
        final PsiType[] conjuncts = ((PsiIntersectionType)castType).getConjuncts();
        for (int i = 1; i < conjuncts.length; i++) {
          PsiType conjunct = conjuncts[i];
          if (TypeConversionUtil.isAssignable(conjuncts[0], conjunct)) {
            redundant = true;
            break;
          }
        }
        if (!redundant) {
          return true;
        }
      }
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
    } else if (parent instanceof PsiConditionalExpression) {
      if (opType instanceof PsiPrimitiveType && !(((PsiConditionalExpression)parent).getType() instanceof PsiPrimitiveType)) {
        if (PsiPrimitiveType.getUnboxedType(PsiTypesUtil.getExpectedTypeByParent((PsiExpression)parent)) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean wrapperCastChangeSemantics(PsiExpression operand, PsiExpression otherOperand, PsiExpression toCast) {
    final boolean isPrimitiveComparisonWithCast;
    final boolean isPrimitiveComparisonWithoutCast;

    if (TypeConversionUtil.isPrimitiveAndNotNull(otherOperand.getType())) {
      // IDEA-111450: A primitive comparison requires one primitive operand and one primitive or wrapper operand.
      isPrimitiveComparisonWithCast = TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(operand.getType());
      isPrimitiveComparisonWithoutCast = TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(toCast.getType());
    }
    else {
      // We do not check whether `otherOperand` is a wrapper, because a reference-to-primitive cast has a
      // side effect regardless of whether we end up doing a primitive or reference comparison.
      isPrimitiveComparisonWithCast = TypeConversionUtil.isPrimitiveAndNotNull(operand.getType());
      isPrimitiveComparisonWithoutCast = TypeConversionUtil.isPrimitiveAndNotNull(toCast.getType());
    }

    // wrapper casted to primitive vs wrapper comparison
    return isPrimitiveComparisonWithCast != isPrimitiveComparisonWithoutCast;
  }

  // see http://download.java.net/jdk7/docs/api/java/lang/invoke/MethodHandle.html#sigpoly
  public static boolean isInPolymorphicCall(final PsiTypeCastExpression typeCast) {
    if (!PsiUtil.isLanguageLevel7OrHigher(typeCast)) return false;

    // return type
    final PsiExpression operand = typeCast.getOperand();
    if (operand instanceof PsiMethodCallExpression) {
      if (isPolymorphicMethod((PsiMethodCallExpression)operand)) return true;
    }

    // argument type
    final PsiElement exprList = typeCast.getParent();
    if (exprList instanceof PsiExpressionList) {
      final PsiElement methodCall = exprList.getParent();
      if (methodCall instanceof PsiMethodCallExpression) {
        if (isPolymorphicMethod((PsiMethodCallExpression)methodCall)) return true;
      }
    }

    return false;
  }

  private static boolean isPolymorphicMethod(PsiMethodCallExpression expression) {
    final PsiElement method = expression.getMethodExpression().resolve();
    return method instanceof PsiMethod &&
           AnnotationUtil.isAnnotated((PsiMethod)method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, false, true);
  }
}