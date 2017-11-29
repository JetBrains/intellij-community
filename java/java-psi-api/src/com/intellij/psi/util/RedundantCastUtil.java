/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
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
  public static List<PsiTypeCastExpression> getRedundantCastsInside(@NotNull PsiElement where) {
    MyCollectingVisitor visitor = new MyCollectingVisitor();
    if (where instanceof PsiEnumConstant) {
      where.accept(visitor);
    }
    else {
      where.acceptChildren(visitor);
    }
    return new ArrayList<>(visitor.myFoundCasts);
  }

  public static boolean isCastRedundant (PsiTypeCastExpression typeCast) {
    PsiElement parent = typeCast.getParent();
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
    if (parent instanceof PsiExpressionList) parent = parent.getParent();
    if (parent instanceof PsiReferenceExpression) parent = parent.getParent();
    if (parent instanceof PsiAnonymousClass) parent = parent.getParent();
    MyIsRedundantVisitor visitor = new MyIsRedundantVisitor(true);
    parent.accept(visitor);
    return visitor.isRedundant;
  }

  @Nullable
  private static PsiExpression deparenthesizeExpression(PsiExpression arg) {
    while (arg instanceof PsiParenthesizedExpression) arg = ((PsiParenthesizedExpression) arg).getExpression();
    return arg;
  }

  public static PsiExpression removeCast(PsiTypeCastExpression castExpression) {
    if (castExpression == null) return null;
    PsiExpression operand = castExpression.getOperand();
    if (operand instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression)operand;
      operand = parExpr.getExpression();
    }
    if (operand == null) return null;

    PsiExpression toBeReplaced = castExpression;

    PsiElement parent = castExpression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      toBeReplaced = (PsiExpression)parent;
      parent = parent.getParent();
    }

    try {
      return (PsiExpression)toBeReplaced.replace(operand);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return toBeReplaced;
  }

  private static class MyCollectingVisitor extends MyIsRedundantVisitor {
    private final Set<PsiTypeCastExpression> myFoundCasts = new HashSet<>();

    private MyCollectingVisitor() {
      super(true);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // avoid multiple visit
    }

    @Override
    public void visitMethod(PsiMethod method) {
      // avoid multiple visit
    }

    @Override
    public void visitField(PsiField field) {
      // avoid multiple visit
    }

    @Override
    protected void addToResults(@NotNull PsiTypeCastExpression typeCast) {
      if (!isTypeCastSemantic(typeCast)) {
        myFoundCasts.add(typeCast);
      }
    }
  }

  private static class MyIsRedundantVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean isRedundant;
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

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      PsiType type = expression.getType();
      if (type instanceof PsiArrayType) {
        for (PsiExpression initializer : expression.getInitializers()) {
          processPossibleTypeCast(initializer, ((PsiArrayType)type).getComponentType());
        }
      }
      super.visitArrayInitializerExpression(expression);
    }

    @Override public void visitVariable(PsiVariable variable) {
      processPossibleTypeCast(variable.getInitializer(), variable.getType());
      super.visitVariable(variable);
    }

    @Override public void visitReturnStatement(PsiReturnStatement statement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class);
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
          PsiType operandType;
          if (castOperand instanceof PsiTypeCastExpression) {
            final PsiExpression nestedCastOperand = ((PsiTypeCastExpression)castOperand).getOperand();
            operandType = nestedCastOperand != null ? nestedCastOperand.getType() : null;
          }
          else if (castOperand instanceof PsiFunctionalExpression && lType != null) {
            final PsiTypeElement typeElement = ((PsiTypeCastExpression)rExpr).getCastType();
            final PsiType castType = typeElement != null ? typeElement.getType() : null;
            operandType = lType.equals(castType) ? castOperand.getType() : null;
          }
          else {
            operandType = castOperand.getType();
          }
          if (operandType != null) {
            if (lType != null && TypeConversionUtil.isAssignable(lType, operandType, false)) {
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
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
        Project project = methodExpr.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        final PsiExpression expressionFromText = factory.createExpressionFromText(methodCall.getText(), methodCall);
        if (!(expressionFromText instanceof PsiMethodCallExpression)) return;
        PsiMethodCallExpression newCall = (PsiMethodCallExpression)expressionFromText;
        PsiExpression newQualifier = newCall.getMethodExpression().getQualifierExpression();
        PsiExpression newOperand = ((PsiTypeCastExpression)((PsiParenthesizedExpression)newQualifier).getExpression()).getOperand();
        newQualifier.replace(newOperand);

        final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
        if (!newResult.isValidResult()) return;
        final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
        PsiType newReturnType = newCall.getType();
        PsiType oldReturnType = methodCall.getType();
        if (newReturnType instanceof PsiCapturedWildcardType && oldReturnType instanceof PsiCapturedWildcardType) {
          newReturnType = ((PsiCapturedWildcardType)newReturnType).getUpperBound();
          oldReturnType = ((PsiCapturedWildcardType)oldReturnType).getUpperBound();
        }
        if (Comparing.equal(newReturnType, oldReturnType) &&
            (Comparing.equal(newTargetMethod, targetMethod) ||
             newTargetMethod.getSignature(newResult.getSubstitutor()).equals(targetMethod.getSignature(resolveResult.getSubstitutor())) &&
             !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated()) &&
             // see SCR11555, SCR14559
             areThrownExceptionsCompatible(targetMethod, newTargetMethod) &&
             areNullnessCompatible(project, targetMethod, newTargetMethod))) {
          addToResults(typeCast);
        }
      }
      catch (IncorrectOperationException ignore) { }
    }

    private static boolean areNullnessCompatible(Project project,
                                                 final PsiMethod oldTargetMethod,
                                                 final PsiMethod newTargetMethod) {
      // the cast may be for the @NotNull which newTargetMethod has whereas the oldTargetMethod doesn't
      NullableNotNullManager nnm = NullableNotNullManager.getInstance(project);
      boolean oldNotNull = nnm.isNotNull(oldTargetMethod, true);
      boolean newNotNull = nnm.isNotNull(newTargetMethod, true);
      if (oldNotNull != newNotNull) return false;
      boolean oldNullable = nnm.isNullable(oldTargetMethod, true);
      boolean newNullable = nnm.isNullable(newTargetMethod, true);
      return oldNullable == newNullable;
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

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      //expression.acceptChildren(this);
    }

    private void processCall(PsiCall expression){
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return;
      PsiExpression[] args = argumentList.getExpressions();
      final JavaResolveResult oldResult = expression.resolveMethodGenerics();
      final PsiElement element = oldResult.getElement();
      if (!(element instanceof PsiMethod)) return;
      PsiMethod oldMethod = (PsiMethod)element;
      PsiParameter[] parameters = oldMethod.getParameterList().getParameters();

      try {
        for (int i = 0; i < args.length; i++) {
          ProgressManager.checkCanceled();
          final PsiExpression arg = deparenthesizeExpression(args[i]);
          if (arg instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = (PsiTypeCastExpression)arg;
            if (i == args.length - 1 && args.length == parameters.length && parameters[i].isVarArgs()) {
              //do not mark cast to resolve ambiguity for calling varargs method with inexact argument
              continue;
            }
            final PsiType typeByParent = PsiTypesUtil.getExpectedTypeByParent(expression);
            final PsiCall newCall;
            if (typeByParent != null) {
              final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
              final String arrayCreationText = "new " + typeByParent.getCanonicalText() + "[] {" + expression.getText() + "}";
              final PsiExpression arrayDeclaration = elementFactory.createExpressionFromText(arrayCreationText, expression);
              newCall = (PsiCall)((PsiNewExpression)arrayDeclaration).getArrayInitializer().getInitializers()[0];
            }
            else {
              final PsiCall call = LambdaUtil.treeWalkUp(expression);
              if (call != null) {
                final PsiCall callCopy = (PsiCall)call.copy();
                newCall = PsiTreeUtil.getParentOfType(callCopy.findElementAt(argumentList.getTextRange().getStartOffset() - call.getTextRange().getStartOffset()), expression.getClass());
              }
              else {
                newCall = (PsiCall)expression.copy();
              }
            }
            final PsiExpressionList argList = newCall.getArgumentList();
            LOG.assertTrue(argList != null);
            PsiExpression[] newArgs = argList.getExpressions();
            LOG.assertTrue(newArgs.length == args.length, "oldCall: " + expression.getText() + "; old length: " + args.length + "; newCall: " + newCall.getText() + "; new length: " + newArgs.length);
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) deparenthesizeExpression(newArgs[i]);
            final PsiTypeElement castTypeElement = cast.getCastType();
            final PsiType castType = castTypeElement != null ? castTypeElement.getType() : null;
            PsiExpression castOperand = castExpression.getOperand();
            if (castOperand == null) return;
            newArgs[i] = (PsiExpression)castExpression.replace(castOperand);
            final JavaResolveResult newResult;
            if (newCall instanceof PsiEnumConstant) {
              // do this manually, because PsiEnumConstantImpl.resolveMethodGenerics() will assert (no containing class for the copy)
              final PsiEnumConstant enumConstant = (PsiEnumConstant)expression;
              PsiClass containingClass = enumConstant.getContainingClass();
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(enumConstant.getProject());
              final PsiClassType type = facade.getElementFactory().createType(containingClass);
              newResult = facade.getResolveHelper().resolveConstructor(type, newCall.getArgumentList(), enumConstant);
            }
            else {
              newResult = newCall.resolveMethodGenerics();
            }

            final PsiAnonymousClass oldAnonymousClass = expression instanceof PsiNewExpression ? ((PsiNewExpression)expression).getAnonymousClass() : null;
            final PsiAnonymousClass newAnonymousClass = newCall instanceof PsiNewExpression ? ((PsiNewExpression)newCall).getAnonymousClass() : null;

            if (oldMethod.equals(newResult.getElement()) &&
                (!(newCall instanceof PsiCallExpression) || 
                 oldAnonymousClass != null && newAnonymousClass != null && Comparing.equal(oldAnonymousClass.getBaseClassType(), newAnonymousClass.getBaseClassType()) || 
                 Comparing.equal(PsiUtil.recaptureWildcards(((PsiCallExpression)newCall).getType(), expression), ((PsiCallExpression)expression).getType())) &&
                newResult.isValidResult()) {
              if (!(newArgs[i] instanceof PsiFunctionalExpression)) {
                addToResults(cast);
              }
              else {
                final boolean varargs = newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).isVarargs();
                final PsiType parameterType = PsiTypesUtil.getParameterType(parameters, i, varargs);
                final PsiType newArgType = newResult.getSubstitutor().substitute(parameterType);

                if (Comparing.equal(castType, ((PsiFunctionalExpression)newArgs[i]).getGroundTargetType(newArgType))) {
                  addToResults(cast);
                }
              }
            }
          }
          else if (arg instanceof PsiLambdaExpression) {
            final PsiType interfaceType = ((PsiLambdaExpression)arg).getFunctionalInterfaceType();
            if (interfaceType != null) {
              List<PsiExpression> expressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)arg);
              for (int returnExprIdx = 0; returnExprIdx < expressions.size(); returnExprIdx++) {
                ProgressManager.checkCanceled();
                PsiExpression returnExpression = deparenthesizeExpression(expressions.get(returnExprIdx));
                if (returnExpression instanceof PsiTypeCastExpression) {
                  processLambdaReturnExpression(expression, i, interfaceType, (PsiTypeCastExpression)returnExpression, returnExprIdx,
                                                expression13 -> (PsiTypeCastExpression)expression13);
                }
                else if (returnExpression instanceof PsiConditionalExpression) {
                  final PsiExpression thenExpression = ((PsiConditionalExpression)returnExpression).getThenExpression();
                  if (thenExpression instanceof PsiTypeCastExpression) {
                    processLambdaReturnExpression(expression, i, interfaceType, (PsiTypeCastExpression)thenExpression,
                                                  returnExprIdx,
                                                  expression12 -> (PsiTypeCastExpression)((PsiConditionalExpression)expression12).getThenExpression());
                  }

                  final PsiExpression elseExpression = ((PsiConditionalExpression)returnExpression).getElseExpression();
                  if (elseExpression instanceof PsiTypeCastExpression) {
                    processLambdaReturnExpression(expression, i, interfaceType, (PsiTypeCastExpression)elseExpression,
                                                  returnExprIdx,
                                                  expression1 -> (PsiTypeCastExpression)((PsiConditionalExpression)expression1).getElseExpression());
                  }
                }
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

    private void processLambdaReturnExpression(PsiCall expression,
                                               int i,
                                               PsiType interfaceType,
                                               PsiTypeCastExpression returnExpression,
                                               int returnExprIdx,
                                               Function<PsiExpression, PsiTypeCastExpression> computeCastExpression) {
      final PsiCall newCall = LambdaUtil.copyTopLevelCall(expression);
      if (newCall == null) return;
      final PsiExpressionList newArgsList = newCall.getArgumentList();
      LOG.assertTrue(newArgsList != null);
      final PsiExpression[] newArgs = newArgsList.getExpressions();
      final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)deparenthesizeExpression(newArgs[i]);
      LOG.assertTrue(lambdaExpression != null, newCall);
      final PsiExpression newReturnExpression = deparenthesizeExpression(LambdaUtil.getReturnExpressions(lambdaExpression).get(returnExprIdx));
      PsiTypeCastExpression castExpression = computeCastExpression.fun(newReturnExpression);
      PsiExpression castOperand = castExpression.getOperand();
      if (castOperand == null) return;
      castOperand = (PsiExpression)castExpression.replace(castOperand);
      final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (interfaceType.equals(functionalInterfaceType)) {
        final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(interfaceType);
        final PsiType castExprType = castOperand.getType();
        if (interfaceReturnType != null && castExprType != null && interfaceReturnType.isAssignableFrom(castExprType)) {
          addToResults(returnExpression);
        }
      }
    }

    @Override public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
      PsiExpression operand = typeCast.getOperand();
      if (operand == null) return;

      PsiExpression expr = deparenthesizeExpression(operand);

      final PsiType topCastType = typeCast.getType();
      if (expr instanceof PsiTypeCastExpression) {
        PsiTypeElement typeElement = ((PsiTypeCastExpression)expr).getCastType();
        if (typeElement == null) return;
        PsiType castType = typeElement.getType();
        final PsiExpression innerOperand = ((PsiTypeCastExpression)expr).getOperand();
        final PsiType operandType = innerOperand != null ? innerOperand.getType() : null;
        if (!(castType instanceof PsiPrimitiveType) && !(topCastType instanceof PsiPrimitiveType)) {
          if (operandType != null && topCastType != null && TypeConversionUtil.areTypesConvertible(operandType, topCastType)) {
            addToResults((PsiTypeCastExpression)expr);
          }
        } else if (Comparing.equal(PsiPrimitiveType.getUnboxedType(operandType), topCastType)) {
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
            if (opposite == null || conditionalType instanceof PsiPrimitiveType &&
                                    !Comparing.equal(conditionalType, opposite.getType())) return;
          }
        } else if (parent instanceof PsiSynchronizedStatement && expr != null && expr.getType() instanceof PsiPrimitiveType) {
          return;
        } else if (expr instanceof PsiLambdaExpression || expr instanceof PsiMethodReferenceExpression) {
          if (parent instanceof PsiParenthesizedExpression && parent.getParent() instanceof PsiReferenceExpression) {
            return;
          }

          final PsiType functionalInterfaceType = PsiTypesUtil.getExpectedTypeByParent(typeCast);
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
          PsiMethod resolve = ((PsiCall)grandGrandPa).resolveMethod();
          if (resolve != null) {
            PsiCall expression = LambdaUtil.copyTopLevelCall((PsiCall)grandGrandPa);
            if (expression == null) return false;
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

      if (parent instanceof PsiLambdaExpression) return;

      if (parent instanceof PsiConditionalExpression) {
        PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
        if (gParent instanceof PsiLambdaExpression) return;
        if (gParent instanceof PsiReturnStatement && 
            PsiTreeUtil.getParentOfType(gParent, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiLambdaExpression) return;
      }

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

          if (opType != null) {
            final PsiExpression expr = PsiUtil.skipParenthesizedExprDown(operand);
            if (expr instanceof PsiConditionalExpression) {
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)expr).getThenExpression())) return;
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)expr).getElseExpression())) return;
            }
          }
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
      if (parent instanceof PsiConditionalExpression) {
        if (castTo instanceof PsiClassType && opType instanceof PsiPrimitiveType && opType != PsiType.NULL) {
          final PsiExpression thenExpression = ((PsiConditionalExpression)parent).getThenExpression();
          final PsiExpression elseExpression = ((PsiConditionalExpression)parent).getElseExpression();
          final PsiExpression opposite = PsiTreeUtil.isAncestor(thenExpression, typeCast, false) ? elseExpression : thenExpression;
          if (opposite != null &&
              !(opposite.getType() instanceof PsiPrimitiveType) &&
              !(PsiTypesUtil.getExpectedTypeByParent(parent) instanceof PsiPrimitiveType)) {
            return;
          }
        }

        if (operand instanceof PsiFunctionalExpression && !castTo.equals(PsiTypesUtil.getExpectedTypeByParent(parent))) {
          return;
        }
      }

      if (arrayAccessAtTheLeftSideOfAssignment(parent, typeCast)) {
        if (TypeConversionUtil.isAssignable(opType, castTo, false) && opType.getArrayDimensions() == castTo.getArrayDimensions()) {
          addToResults(typeCast);
        }
      }
      else {
        if (parent instanceof PsiInstanceOfExpression && opType instanceof PsiPrimitiveType) {
          return;
        }
        if (parent instanceof PsiForeachStatement) {
          final PsiClassType.ClassResolveResult castResolveResult = PsiUtil.resolveGenericsClassInType(opType);
          final PsiClass psiClass = castResolveResult.getElement();
          if (psiClass != null) {
            final PsiClass iterableClass = JavaPsiFacade.getInstance(parent.getProject()).findClass(CommonClassNames.JAVA_LANG_ITERABLE, psiClass.getResolveScope());
            if (iterableClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, iterableClass, true)) {
              final PsiTypeParameter[] iterableTypeParameters = iterableClass.getTypeParameters();
              if (iterableTypeParameters.length == 1) {
                final PsiType resultedParamType = TypeConversionUtil.getSuperClassSubstitutor(iterableClass, psiClass, castResolveResult.getSubstitutor()).substitute(iterableTypeParameters[0]);
                if (resultedParamType != null && 
                    TypeConversionUtil.isAssignable(((PsiForeachStatement)parent).getIterationParameter().getType(), resultedParamType)) {
                  addToResults(typeCast);
                  return;
                }
              }
            } 
          } else {
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
        if (parent instanceof PsiInstanceOfExpression) {
          //15.20.2. Type Comparison Operator instanceof:
          //If a cast (p15.16) of the RelationalExpression to the ReferenceType would be rejected as a compile-time error,
          //then the instanceof relational expression likewise produces a compile-time error.
          final PsiTypeElement checkTypeElement = ((PsiInstanceOfExpression)parent).getCheckType();
          if (checkTypeElement != null && TypeConversionUtil.areTypesConvertible(opType, checkTypeElement.getType())) {
            addToResults(typeCast);
          }
        }
        else if (TypeConversionUtil.isAssignable(castTo, opType, false) &&
                 (expectedTypeByParent == null || TypeConversionUtil.isAssignable(expectedTypeByParent, opType, false))) {
          addToResults(typeCast);
        }
      }
    }

    private static boolean isApplicableForConditionalBranch(PsiType opType, PsiExpression thenExpression) {
      if (thenExpression != null) {
        final PsiType thenType = thenExpression.getType();
        if (thenType != null && !TypeConversionUtil.isAssignable(opType, thenType)) {
          return false;
        }
      }
      return true;
    }

    private static boolean arrayAccessAtTheLeftSideOfAssignment(PsiElement parent, PsiElement element) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(parent, PsiAssignmentExpression.class, false, PsiMember.class);
      if (assignment == null) return false;
      PsiExpression lExpression = assignment.getLExpression();
      return lExpression instanceof PsiArrayAccessExpression &&
             PsiTreeUtil.isAncestor(lExpression, parent, false) &&
             !isIndexExpression(element, (PsiArrayAccessExpression)lExpression);
    }

    private static boolean isIndexExpression(PsiElement element, PsiArrayAccessExpression arrayAccessExpression) {
      if (PsiTreeUtil.isAncestor(arrayAccessExpression.getIndexExpression(), element, false)) {
        return true;
      }
      PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      if (arrayExpression instanceof PsiArrayAccessExpression) {
        return isIndexExpression(element, (PsiArrayAccessExpression)arrayExpression);
      }
      return false;
    }
  }

  private static boolean isCastRedundantInRefExpression (final PsiReferenceExpression refExpression, final PsiExpression castOperand) {
    if (refExpression.getParent() instanceof PsiMethodCallExpression) return false;
    final PsiElement resolved = refExpression.resolve();
    try {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(refExpression.getProject()).getElementFactory();
      final PsiExpression copyExpression = elementFactory.createExpressionFromText(refExpression.getText(), refExpression);
      if (copyExpression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression copy = (PsiReferenceExpression)copyExpression;
        final PsiExpression qualifier = copy.getQualifierExpression();
        if (qualifier != null) {
          qualifier.replace(castOperand);
          return copy.resolve() == resolved;
        }
      }
    }
    catch (IncorrectOperationException ignore) { }
    return false;
  }

  private static boolean isTypeCastSemantic(PsiTypeCastExpression typeCast) {
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
    }

    final PsiExpression stripParenthesisOperand = PsiUtil.skipParenthesizedExprDown(operand);
    if (stripParenthesisOperand instanceof PsiFunctionalExpression) {
      if (isCastToSerializable(castType)) return true;
    }
    else if (stripParenthesisOperand instanceof PsiConditionalExpression) {
      final PsiExpression thenExpr = PsiUtil.skipParenthesizedExprDown(((PsiConditionalExpression)stripParenthesisOperand).getThenExpression());
      final PsiExpression elseExpr = PsiUtil.skipParenthesizedExprDown(((PsiConditionalExpression)stripParenthesisOperand).getElseExpression());
      if (thenExpr instanceof PsiFunctionalExpression || elseExpr instanceof PsiFunctionalExpression) {
        return true;
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
      if (otherOperand != null && wrapperCastChangeSemantics(firstOperand, otherOperand, operand)) {
        return true;
      }
    }
    else if (parent instanceof PsiConditionalExpression) {
      if (opType instanceof PsiPrimitiveType && !(((PsiConditionalExpression)parent).getType() instanceof PsiPrimitiveType)) {
        if (PsiPrimitiveType.getUnboxedType(PsiTypesUtil.getExpectedTypeByParent(parent)) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isCastToSerializable(PsiType castType) {
    return InheritanceUtil.isInheritor(castType, CommonClassNames.JAVA_IO_SERIALIZABLE);
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