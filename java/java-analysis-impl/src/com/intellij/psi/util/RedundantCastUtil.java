// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RedundantCastUtil {
  private static final Logger LOG = Logger.getInstance(RedundantCastUtil.class);
  private static final Key<PsiElement> SELF_REFERENCE = Key.create("SELF_REFERENCE");

  private RedundantCastUtil() { }

  @NotNull
  public static List<PsiTypeCastExpression> getRedundantCastsInside(@NotNull PsiElement where) {
    MyCollectingVisitor visitor = new MyCollectingVisitor();
    if (where instanceof PsiClass) {
      where.acceptChildren(visitor);
    }
    else {
      where.accept(visitor);
    }
    return new ArrayList<>(visitor.myFoundCasts);
  }

  public static boolean isCastRedundant(PsiTypeCastExpression typeCast) {
    PsiElement parent = typeCast.getParent();
    PsiExpression operand = typeCast.getOperand();
    if (operand != null && operand.getType() != null && operand.getType().equals(typeCast.getType())) return true;
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
    if (parent instanceof PsiExpressionList) parent = parent.getParent();
    if (parent instanceof PsiReferenceExpression) parent = parent.getParent();
    if (parent instanceof PsiAnonymousClass) parent = parent.getParent();
    MyIsRedundantVisitor visitor = new MyIsRedundantVisitor();
    parent.accept(visitor);
    return visitor.foundRedundantCast == typeCast;
  }

  @Nullable
  private static PsiExpression deparenthesizeExpression(PsiExpression arg) {
    return PsiUtil.skipParenthesizedExprDown(arg);
  }

  private static class MyCollectingVisitor extends MyIsRedundantVisitor {
    private final Set<PsiTypeCastExpression> myFoundCasts = new HashSet<>();

    @Override
    public void visitClass(PsiClass aClass) {
      // avoid multiple visit
    }

    @Override
    protected void registerCast(@NotNull PsiTypeCastExpression typeCast) {
      myFoundCasts.add(typeCast);
    }
  }

  @SuppressWarnings("UnsafeReturnStatementVisitor")
  private static class MyIsRedundantVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiTypeCastExpression foundRedundantCast;

    private void addToResults(@NotNull PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantic(typeCast)) {
        registerCast(typeCast);
      }
    }

    private void addIfNarrowing(PsiTypeCastExpression castExpression, PsiType opType, PsiType expectedTypeByParent) {
      PsiTypeElement castElement = castExpression.getCastType();
      if (castElement != null && TypeConversionUtil.isAssignable(castElement.getType(), opType, false) &&
          (expectedTypeByParent == null || TypeConversionUtil.isAssignable(expectedTypeByParent, opType, false))) {
        addToResults(castExpression);
      }
    }

    private void addIfNarrowing(PsiExpression expression, PsiType expectedTypeByParent) {
      expression = deparenthesizeExpression(expression);
      if (expression instanceof PsiTypeCastExpression) {
        PsiExpression operand = getInnerMostOperand(expression);
        if (operand != null) {
          addIfNarrowing((PsiTypeCastExpression)expression, operand.getType(), expectedTypeByParent);
        }
      }
    }

    protected void registerCast(@NotNull PsiTypeCastExpression typeCast) {
      foundRedundantCast = typeCast;
      stopWalking();
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
        PsiType lType = processBinaryExpressionOperand(deparenthesizeExpression(operands[0]), operands[1].getType(), tokenType);
        
        for (int i = 1; i < operands.length; i++) {
          PsiExpression operand = deparenthesizeExpression(operands[i]);
          if (operand == null) continue;
          PsiType rType = processBinaryExpressionOperand(operand, lType, tokenType);
          lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, tokenType, true);
        }
      }
      processNestedCasts(expression.getOperands());
      super.visitPolyadicExpression(expression);
    }

    private @Nullable PsiType processBinaryExpressionOperand(final PsiExpression operand,
                                                             final PsiType otherType,
                                                             final IElementType binaryToken) {
      if (operand instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression toCast = typeCast.getOperand();
        if (toCast != null && otherType != null && TypeConversionUtil.isBinaryOperatorApplicable(binaryToken, toCast.getType(), otherType, false)) {
          addToResults(typeCast);
          return toCast.getType();
        }
      }
      return operand != null ? operand.getType() : null;
    }

    private void processPossibleTypeCast(PsiExpression rExpr, @Nullable PsiType lType) {
      rExpr = deparenthesizeExpression(rExpr);
      if (rExpr instanceof PsiTypeCastExpression) {
        PsiExpression castOperand = getInnerMostOperand(((PsiTypeCastExpression)rExpr).getOperand());
        if (castOperand != null) {
          if (castOperand instanceof PsiFunctionalExpression) {
            if (lType != null) {
              final PsiTypeElement typeElement = ((PsiTypeCastExpression)rExpr).getCastType();
              final PsiType castType = typeElement != null ? typeElement.getType() : null;
              if (lType.equals(castType)) {
                addToResults((PsiTypeCastExpression)rExpr);
              }
            }
            return;
          }
          PsiType opType = getOpTypeWithExpected(castOperand, lType);
          if (opType != null) {
            if (castOperand instanceof PsiConditionalExpression) {
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)castOperand).getThenExpression())) return;
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)castOperand).getElseExpression())) return;
            }
            if (lType != null && TypeConversionUtil.isAssignable(lType, opType, false)) {
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      processCall(expression);

      super.visitMethodCallExpression(expression);
    }

    private static boolean areNullabilityCompatible(final PsiMethod oldTargetMethod,
                                                    final PsiMethod newTargetMethod) {
      // the cast may be for the @NotNull which newTargetMethod has whereas the oldTargetMethod doesn't
      Nullability oldNullability = NullableNotNullManager.getNullability(oldTargetMethod);
      Nullability newNullability = NullableNotNullManager.getNullability(newTargetMethod);
      return oldNullability == newNullability;
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
      PsiExpression qualifier = deparenthesizeExpression(expression.getQualifierExpression());
      if (qualifier instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)qualifier;
        PsiExpression operand = getInnerMostOperand(typeCast.getOperand());
        if (operand == null) return;

        PsiTypeElement typeElement = typeCast.getCastType();
        if (typeElement == null) return;
        PsiType opType = operand.getType();
        if (opType == null) return;

        if (!(operand instanceof PsiFunctionalExpression || opType instanceof PsiPrimitiveType) &&
            isCastInReferenceQualifierRedundant(expression)) {
          addToResults(typeCast);
        }
      }
      super.visitReferenceExpression(expression);
    }

    private static PsiExpression getInnerMostOperand(@Nullable PsiExpression castOperand) {
      castOperand = deparenthesizeExpression(castOperand);
      while (castOperand instanceof PsiTypeCastExpression) {
        castOperand = deparenthesizeExpression(((PsiTypeCastExpression)castOperand).getOperand());
      }
      return castOperand;
    }

    private static boolean isCastInReferenceQualifierRedundant(final PsiReferenceExpression refExpression) {
      final JavaResolveResult resolveResult = refExpression.advancedResolve(false);
      PsiElement oldMember = resolveResult.getElement();
      if (oldMember == null) return false;
      try {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(refExpression.getParent());
        if (parent instanceof PsiMethodCallExpression) {
          PsiMethod targetMethod = (PsiMethod)oldMember;
          if (targetMethod.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
          }
          PsiMethodCallExpression newCall =
            (PsiMethodCallExpression)copyCallExpression(((PsiMethodCallExpression)parent), PsiTypesUtil.getExpectedTypeByParent(parent));
          if (newCall == null) return false;
          PsiExpression newQualifier = deparenthesizeExpression(newCall.getMethodExpression().getQualifierExpression());
          LOG.assertTrue(newQualifier != null);
          PsiElement oldReference = newQualifier.getCopyableUserData(SELF_REFERENCE);
          PsiElement replace = newQualifier.replace(getInnerMostOperand(newQualifier));
          replace.putCopyableUserData(SELF_REFERENCE, oldReference);

          final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
          if (!newResult.isValidResult()) return false;

          final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
          LOG.assertTrue(newTargetMethod != null, "isValidResult() check above should find this");

          PsiType newReturnType = newCall.getType();
          PsiType oldReturnType = ((PsiMethodCallExpression)parent).getType();

          if (Comparing.equal(newReturnType == null ? null : createTypeMapper().mapType(newReturnType), oldReturnType) &&
              (Comparing.equal(newTargetMethod, targetMethod) ||
               !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated()) &&
               MethodSignatureUtil.isSuperMethod(newTargetMethod, targetMethod) &&
               // see SCR11555, SCR14559
               areThrownExceptionsCompatible(targetMethod, newTargetMethod) &&
               areNullabilityCompatible(targetMethod, newTargetMethod))) {
            return true;
          }
          return false;
        }
        else {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(refExpression.getProject());
          final PsiReferenceExpression newExpression = (PsiReferenceExpression)elementFactory.createExpressionFromText(refExpression.getText(), refExpression);
          final PsiExpression newQualifier = newExpression.getQualifierExpression();
          LOG.assertTrue(newQualifier != null);
          newQualifier.replace(getInnerMostOperand(newQualifier));

          JavaResolveResult newResult = newExpression.advancedResolve(false);
          if (!newResult.isValidResult()) return false;

          return oldMember.equals(newResult.getElement());
        }
      }
      catch (IncorrectOperationException ignore) { 
        return false;
      }
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
        PsiCall newCall = null;
        for (int i = 0; i < args.length; i++) {
          ProgressManager.checkCanceled();
          final PsiExpression arg = deparenthesizeExpression(args[i]);
          if (arg instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = (PsiTypeCastExpression)arg;
            final PsiType typeByParent = PsiTypesUtil.getExpectedTypeByParent(expression);
            if (newCall == null) {
              newCall = copyCallExpression(expression, typeByParent);
            }
            if (newCall == null) return;
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

            if (i == args.length - 1 && args.length == parameters.length && parameters[i].isVarArgs() &&
                (ExpressionUtils.isNullLiteral(cast.getOperand()) ||
                 oldResult instanceof MethodCandidateInfo && newResult instanceof MethodCandidateInfo &&
                 ((MethodCandidateInfo)oldResult).getApplicabilityLevel() != ((MethodCandidateInfo)newResult).getApplicabilityLevel())) {
              //do not mark cast to resolve ambiguity for calling varargs method with inexact argument
              continue;
            }

            if (oldMethod.equals(newResult.getElement()) &&
                newResult.isValidResult() &&
                !(newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).getInferenceErrorMessage() != null) &&
                recapture(newResult.getSubstitutor()).equals(oldResult.getSubstitutor())) {
              PsiExpression newArg = PsiUtil.deparenthesizeExpression(newArgs[i]);
              if (newArg instanceof PsiFunctionalExpression) {
                final boolean varargs = newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).isVarargs();
                final PsiType parameterType = PsiTypesUtil.getParameterType(parameters, i, varargs);
                PsiType newArgType = newResult.getSubstitutor().substitute(parameterType);

                if (newResult instanceof MethodCandidateInfo && PsiUtil.isRawSubstitutor(((MethodCandidateInfo)newResult).getElement(), newResult.getSubstitutor())) {
                  newArgType = TypeConversionUtil.erasure(newArgType);
                }
                
                if (Comparing.equal(castType, ((PsiFunctionalExpression)newArg).getGroundTargetType(newArgType))) {
                  addToResults(cast);
                }
                else {
                  newArg.replace(arg);
                }
              }
              else {
                addToResults(cast);
              }
            }
            else {
              newArgs[i].replace(arg);
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
                  final PsiExpression thenExpression = deparenthesizeExpression(((PsiConditionalExpression)returnExpression).getThenExpression());
                  if (thenExpression instanceof PsiTypeCastExpression) {
                    processLambdaReturnExpression(expression, i, interfaceType, (PsiTypeCastExpression)thenExpression,
                                                  returnExprIdx,
                                                  expression12 -> (PsiTypeCastExpression)deparenthesizeExpression(((PsiConditionalExpression)expression12).getThenExpression()));
                  }

                  final PsiExpression elseExpression = deparenthesizeExpression(((PsiConditionalExpression)returnExpression).getElseExpression());
                  if (elseExpression instanceof PsiTypeCastExpression) {
                    processLambdaReturnExpression(expression, i, interfaceType, (PsiTypeCastExpression)elseExpression,
                                                  returnExprIdx,
                                                  expression1 -> (PsiTypeCastExpression)deparenthesizeExpression(((PsiConditionalExpression)expression1).getElseExpression()));
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

      processNestedCasts(args);
    }

    private static PsiSubstitutor recapture(PsiSubstitutor substitutor) {
      PsiTypeMapper typeMapper = createTypeMapper();
      PsiSubstitutor result = PsiSubstitutor.EMPTY;
      for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
        PsiType value = entry.getValue();
        result = result.put(entry.getKey(), value == null ? null : typeMapper.mapType(value));
      }
      return result;
    }

    @NotNull
    private static PsiTypeMapper createTypeMapper() {
      return new PsiTypeMapper() {
        @Override
        public PsiType visitType(@NotNull PsiType type) {
          return type;
        }

        @Override
        public PsiType visitClassType(@NotNull PsiClassType classType) {
          final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
          final PsiClass psiClass = classResolveResult.getElement();
          final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
          if (psiClass == null) return classType;
          return new PsiImmediateClassType(psiClass, recapture(substitutor));
        }

        @Override
        public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
          PsiElement context = capturedWildcardType.getContext();
          @Nullable PsiElement original = context.getCopyableUserData(SELF_REFERENCE);
          if (original != null) {
            context = original;
          }
          PsiCapturedWildcardType mapped =
            PsiCapturedWildcardType.create(capturedWildcardType.getWildcard(), context, capturedWildcardType.getTypeParameter());

          mapped.setUpperBound(capturedWildcardType.getUpperBound(false).accept(this));

          return mapped;
        }
      };
    }

    private void processNestedCasts(PsiExpression[] args) {
      for (PsiExpression arg : args) {
        arg = deparenthesizeExpression(arg);
        if (arg instanceof PsiTypeCastExpression) {
          PsiExpression castOperand = ((PsiTypeCastExpression)arg).getOperand();
          if (castOperand != null) {
            castOperand.accept(this);
          }
        }
        else if (arg != null) {
          arg.accept(this);
        }
      }
    }
    
    private static void encode(PsiElement expression) {
      expression.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof PsiExpression) {
            element.putCopyableUserData(SELF_REFERENCE, element);
          }
          super.visitElement(element);
        }
      });
    }
   
    private static void clean(PsiElement expression) {
      expression.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof PsiExpression) {
            element.putCopyableUserData(SELF_REFERENCE, null);
          }
          super.visitElement(element);
        }
      });
    }

    @Nullable
    private static PsiCall copyCallExpression(PsiCall expression, PsiType typeByParent) {
      PsiElement encoded = null;
      try {
        if (typeByParent != null) {
          encode(encoded = expression);
          return  (PsiCall)LambdaUtil.copyWithExpectedType(expression, typeByParent);
        }
        else {
          final PsiCall call = LambdaUtil.treeWalkUp(expression);
          if (call != null) {
            encode(encoded = call);
            Object marker = new Object();
            PsiTreeUtil.mark(expression, marker);
            final PsiCall callCopy = LambdaUtil.copyTopLevelCall(call);
            if (callCopy == null) return null;
            return  (PsiCall)PsiTreeUtil.releaseMark(callCopy, marker);
          }
          else {
            encode(encoded = expression);
            return (PsiCall)expression.copy();
          }
        }
      }
      finally {
        if (encoded != null) {
          clean(encoded);
        }
      }
    }

    private void processLambdaReturnExpression(PsiCall expression,
                                               int i,
                                               PsiType interfaceType,
                                               PsiTypeCastExpression returnExpression,
                                               int returnExprIdx,
                                               Function<? super PsiExpression, ? extends PsiTypeCastExpression> computeCastExpression) {
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
        PsiTypeCastExpression innerCast = (PsiTypeCastExpression)expr;
        PsiTypeElement typeElement = innerCast.getCastType();
        if (typeElement == null) return;
        PsiType castType = typeElement.getType();
        final PsiExpression innerOperand = innerCast.getOperand();
        final PsiType operandType = innerOperand != null ? innerOperand.getType() : null;
        if (!(castType instanceof PsiPrimitiveType) && !(topCastType instanceof PsiPrimitiveType)) {
          if (operandType != null && topCastType != null && TypeConversionUtil.areTypesConvertible(operandType, topCastType)) {
            addToResults(innerCast);
          }
        } else if (Comparing.equal(PsiPrimitiveType.getUnboxedType(operandType), topCastType)) {
          addToResults(innerCast);
        }
        else if (operandType != null && operandType.equals(castType)) {
          // like (int)(long)1L
          addToResults(innerCast);
        }
      }
      super.visitTypeCastExpression(typeCast);
    }

    private static boolean checkResolveAfterRemoveCast(PsiElement parent) {
      PsiElement grandPa = PsiUtil.skipParenthesizedExprUp(parent.getParent());
      if (grandPa instanceof PsiExpressionList) {
        int idx = LambdaUtil.getLambdaIdx((PsiExpressionList)grandPa, parent);
        PsiElement grandGrandPa = grandPa.getParent();
        if (grandGrandPa instanceof PsiCall) {
          PsiMethod resolve = ((PsiCall)grandGrandPa).resolveMethod();
          if (resolve != null) {
            PsiCall expression = LambdaUtil.copyTopLevelCall((PsiCall)grandGrandPa);
            if (expression == null) return false;
            PsiExpressionList argumentList = expression.getArgumentList();
            LOG.assertTrue(argumentList != null);
            PsiExpression toReplace = deparenthesizeExpression(argumentList.getExpressions()[idx]);
            if (toReplace instanceof PsiConditionalExpression) {
              PsiExpression thenExpression = deparenthesizeExpression(((PsiConditionalExpression)toReplace).getThenExpression());
              PsiExpression elseExpression = deparenthesizeExpression(((PsiConditionalExpression)toReplace).getElseExpression());
              if (thenExpression instanceof PsiTypeCastExpression) {
                final PsiExpression thenOperand = ((PsiTypeCastExpression)thenExpression).getOperand();
                if (thenOperand != null) {
                  thenExpression.replace(thenOperand);
                }
              }
              if (elseExpression instanceof PsiTypeCastExpression) {
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

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      PsiExpression iteratedValue = deparenthesizeExpression(statement.getIteratedValue());
      if (iteratedValue instanceof PsiTypeCastExpression) {
        PsiExpression operand = ((PsiTypeCastExpression)iteratedValue).getOperand();
        if (operand != null) {
          PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(operand.getType(), statement.getResolveScope());
          if (collectionItemType != null && TypeConversionUtil.isAssignable(statement.getIterationParameter().getType(), collectionItemType)) {
            addToResults((PsiTypeCastExpression)iteratedValue);
          }
        }
      }
      super.visitForeachStatement(statement);
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      final PsiTypeElement checkTypeElement = expression.getCheckType();
      if (checkTypeElement == null) return;
      PsiExpression typeCast = deparenthesizeExpression(expression.getOperand());
      if (typeCast instanceof PsiTypeCastExpression) {
        PsiExpression operand = getInnerMostOperand(((PsiTypeCastExpression)typeCast).getOperand());
        if (operand != null) {
          PsiType opType = operand.getType();
          //15.20.2. Type Comparison Operator instanceof:
          //If a cast (p15.16) of the RelationalExpression to the ReferenceType would be rejected as a compile-time error,
          //then the instanceof relational expression likewise produces a compile-time error.
          if (opType != null &&
              !(opType instanceof PsiPrimitiveType) &&
              TypeConversionUtil.areTypesConvertible(opType, checkTypeElement.getType())) {
            addToResults((PsiTypeCastExpression)typeCast);
          }
        }
      }
      super.visitInstanceOfExpression(expression);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      PsiExpression typeCast = deparenthesizeExpression(statement.getException());
      if (typeCast instanceof PsiTypeCastExpression) {
        PsiExpression operand = getInnerMostOperand(((PsiTypeCastExpression)typeCast).getOperand());
        if (operand != null) {
          PsiType opType = operand.getType();
          final PsiClass thrownClass = PsiUtil.resolveClassInType(opType);
          if (InheritanceUtil.isInheritor(thrownClass, false, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
            addToResults((PsiTypeCastExpression)typeCast);
          }
          if (InheritanceUtil.isInheritor(thrownClass, false, CommonClassNames.JAVA_LANG_THROWABLE)) {
            final PsiParameterListOwner listOwner = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
            if (listOwner instanceof PsiMethod) {
              processThrowsList((PsiMethod)listOwner, PsiSubstitutor.EMPTY, (PsiTypeCastExpression)typeCast, opType);
            }
            else if (listOwner instanceof PsiLambdaExpression) {
              PsiType functionalInterfaceType = ((PsiLambdaExpression)listOwner).getFunctionalInterfaceType();
              final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
              final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
              if (interfaceMethod != null) {
                processThrowsList(interfaceMethod, LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult), (PsiTypeCastExpression)typeCast, opType);
              }
            }
          }
        }
      }

      super.visitThrowStatement(statement);
    }

    private void processThrowsList(PsiMethod interfaceMethod,
                                   PsiSubstitutor psiSubstitutor,
                                   PsiTypeCastExpression typeCast,
                                   PsiType opType) {
      for (PsiClassType thrownType : interfaceMethod.getThrowsList().getReferencedTypes()) {
        PsiType left = psiSubstitutor.substitute(thrownType);
        if (left != null && TypeConversionUtil.isAssignable(left, opType, false)) {
          addToResults(typeCast);
        }
      }
    }

    @Override
    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      PsiExpression lockExpression = deparenthesizeExpression(statement.getLockExpression());
      if (lockExpression instanceof PsiTypeCastExpression) {
        PsiExpression operand = getInnerMostOperand(((PsiTypeCastExpression)lockExpression).getOperand());
        if (operand != null) {
          PsiType opType = operand.getType();
          if (!(operand instanceof PsiFunctionalExpression) && !(opType instanceof PsiPrimitiveType) && opType != null) {
            addIfNarrowing((PsiTypeCastExpression)lockExpression, opType, null);
          }
        }
      }
      super.visitSynchronizedStatement(statement);
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
      visitSwitchBlockSelector(statement);
      super.visitSwitchStatement(statement);
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      visitSwitchBlockSelector(expression);

      PsiType expectedTypeByParent = PsiTypesUtil.getExpectedTypeByParent(expression);
      for (PsiExpression resultExpression : PsiUtil.getSwitchResultExpressions(expression)) {
        addIfNarrowing(resultExpression, expectedTypeByParent);
      }

      super.visitSwitchExpression(expression);
    }

    private void visitSwitchBlockSelector(PsiSwitchBlock expression) {
      PsiExpression switchVariable = deparenthesizeExpression(expression.getExpression());
      if (switchVariable instanceof PsiTypeCastExpression) {
        PsiExpression operand = ((PsiTypeCastExpression)switchVariable).getOperand();
        if (operand != null) {
          PsiType opType = operand.getType();
          if (opType instanceof PsiClassType && PsiPrimitiveType.getUnboxedType(opType) == null && !opType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            PsiClass aClass = ((PsiClassType)opType).resolve();
            if (aClass != null && !aClass.isEnum()) {
              return;
            }
          }
          addIfNarrowing((PsiTypeCastExpression)switchVariable, opType, null);
        } 
      }
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      addIfNarrowing(statement.getAssertCondition(), PsiType.BOOLEAN);
      addIfNarrowing(statement.getAssertDescription(), PsiType.getJavaLangString(statement.getManager(), statement.getResolveScope()));
      super.visitAssertStatement(statement);
    }
    

    @Override
    public void visitYieldStatement(PsiYieldStatement statement) {
      PsiSwitchExpression switchExpression = statement.findEnclosingExpression();
      PsiType expectedTypeByParent = switchExpression != null ? PsiTypesUtil.getExpectedTypeByParent(switchExpression) : null;
      addIfNarrowing(statement.getExpression(), expectedTypeByParent);
      super.visitYieldStatement(statement);
    }
    
    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      addIfNarrowing(statement.getCondition(), PsiType.BOOLEAN);
      super.visitDoWhileStatement(statement);
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      addIfNarrowing(statement.getCondition(), PsiType.BOOLEAN);
      super.visitIfStatement(statement);
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      addIfNarrowing(statement.getCondition(), PsiType.BOOLEAN);
      super.visitWhileStatement(statement);
    }

    @Override
    public void visitResourceExpression(PsiResourceExpression expression) {
      addIfNarrowing(expression.getExpression(), null);
      super.visitResourceExpression(expression);
    }

    @Override
    public void visitExpressionStatement(PsiExpressionStatement statement) {
      if (!(statement.getParent() instanceof PsiSwitchLabeledRuleStatement)) {
        addIfNarrowing(statement.getExpression(), null);
      }
      super.visitExpressionStatement(statement);
    }

    @Override
    public void visitNameValuePair(PsiNameValuePair pair) {
      PsiAnnotationMemberValue value = pair.getValue();
      if (value instanceof PsiExpression) {
        addIfNarrowing((PsiExpression)value, null);
      }
      super.visitNameValuePair(pair);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      if (!(PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiExpressionList)) {
        PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(expression);
        if (method != null) {
          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            List<PsiExpression> returns = LambdaUtil.getReturnExpressions(expression);
            for (PsiExpression aReturn : returns) {
              PsiExpression returnInLambda = deparenthesizeExpression(aReturn);
              if (returnInLambda instanceof PsiTypeCastExpression) {
                PsiExpression operand = getInnerMostOperand(returnInLambda);
                if (operand != null && returnType.equals(operand.getType())) {
                  addToResults((PsiTypeCastExpression)returnInLambda);
                }
              }
            }
          }
        }
      }
      super.visitLambdaExpression(expression);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression conditionalExpression) {
      PsiElement gParent = PsiUtil.skipParenthesizedExprUp(conditionalExpression.getParent());
      if (gParent instanceof PsiLambdaExpression) return;
      if (gParent instanceof PsiReturnStatement &&
          PsiTreeUtil.getParentOfType(gParent, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiLambdaExpression) return;

      PsiExpression thenExpression = deparenthesizeExpression(conditionalExpression.getThenExpression()); 
      PsiExpression elseExpression = deparenthesizeExpression(conditionalExpression.getElseExpression()); 
      if (thenExpression instanceof PsiTypeCastExpression) {
        visitConditional((PsiTypeCastExpression)thenExpression, conditionalExpression, getInnerMostOperand(elseExpression));
      }
      if (elseExpression instanceof PsiTypeCastExpression) {
        visitConditional((PsiTypeCastExpression)elseExpression, conditionalExpression, getInnerMostOperand(thenExpression));
      }

      addIfNarrowing(conditionalExpression.getCondition(), PsiType.BOOLEAN);
      super.visitConditionalExpression(conditionalExpression);
    }

    private void visitConditional(PsiTypeCastExpression typeCast, @NotNull PsiConditionalExpression parent, @Nullable PsiExpression oppositeOperand) {
      final PsiExpression operand = getInnerMostOperand(typeCast.getOperand());
      if (operand == null) return;
      PsiType castTo = typeCast.getType();
      if (castTo == null) return;
      if (operand instanceof PsiFunctionalExpression && !castTo.equals(PsiTypesUtil.getExpectedTypeByParent(parent))) {
        return;
      }

      PsiType opType = operand.getType();
      
      if (castTo instanceof PsiClassType && opType instanceof PsiPrimitiveType && opType != PsiType.NULL) {
        if (oppositeOperand != null &&
            !(oppositeOperand.getType() instanceof PsiPrimitiveType) &&
            !(PsiTypesUtil.getExpectedTypeByParent(parent) instanceof PsiPrimitiveType)) {
          return;
        }
      }
   
      final PsiType conditionalType = parent.getType();
      if (!Comparing.equal(opType, conditionalType)) {
        if (!PsiUtil.isLanguageLevel5OrHigher(typeCast)) {
          return;
        }
        if (!checkResolveAfterRemoveCast(parent)) return;
        if (!PsiPolyExpressionUtil.isPolyExpression(parent)) {    //branches need to be of the same type
          if (oppositeOperand == null || !Comparing.equal(conditionalType, oppositeOperand.getType())) return;
        }
      }
      addIfNarrowing(typeCast, opType, null);
    }

    private static PsiType getOpTypeWithExpected(PsiExpression operand, PsiType expectedTypeByParent) {
      PsiType opType = operand.getType();

      if (expectedTypeByParent != null) {
        try {
          final Project project = operand.getProject();
          final String uniqueVariableName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("l", operand, false);
          final PsiDeclarationStatement declarationStatement =
            (PsiDeclarationStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(
              expectedTypeByParent.getCanonicalText() + " " + uniqueVariableName + " = " + operand.getText() + ";", operand);
          final PsiExpression initializer = ((PsiLocalVariable)declarationStatement.getDeclaredElements()[0]).getInitializer();
          LOG.assertTrue(initializer != null, operand.getText());
          opType = initializer.getType();

          if (initializer instanceof PsiMethodCallExpression) {
            JavaResolveResult newResult = ((PsiMethodCallExpression)initializer).resolveMethodGenerics();
            if (newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).getInferenceErrorMessage() != null) {
              return null;
            }
          }
        }
        catch (IncorrectOperationException ignore) {}
      }
      return opType;
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

    @Override
    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      PsiExpression arrayExpression = deparenthesizeExpression(expression.getArrayExpression());
      if (arrayExpression instanceof PsiTypeCastExpression) {
        PsiTypeElement castTypeElement = ((PsiTypeCastExpression)arrayExpression).getCastType();
        PsiExpression operand = ((PsiTypeCastExpression)arrayExpression).getOperand();
        if (castTypeElement != null && operand != null) {
          if (PsiUtil.isAccessedForWriting(expression)) {
            PsiType castTo = castTypeElement.getType();
            PsiType opType = operand.getType();
            if (opType != null && TypeConversionUtil.isAssignable(opType, castTo, false) && opType.getArrayDimensions() == castTo.getArrayDimensions()) {
              addToResults((PsiTypeCastExpression)arrayExpression);
            }
          }
        }
      }
      
      PsiExpression indexExpression = deparenthesizeExpression(expression.getIndexExpression());
      if (indexExpression instanceof PsiTypeCastExpression) {
        PsiExpression operand = ((PsiTypeCastExpression)indexExpression).getOperand();
        if (operand != null) {
          PsiType opType = operand.getType();
          if (opType != null) {
            addIfNarrowing((PsiTypeCastExpression) indexExpression, opType, null);
          }
        }
      }
      super.visitArrayAccessExpression(expression);
    }
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
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(typeCast.getParent());
        if ((parent instanceof PsiReturnStatement || parent instanceof PsiExpressionList || parent instanceof PsiVariable ||
            parent instanceof PsiAssignmentExpression || 
             parent instanceof PsiArrayAccessExpression && PsiTreeUtil.isAncestor(((PsiArrayAccessExpression)parent).getIndexExpression(), typeCast, false)) &&
            castType.equals(ExpectedTypeUtils.findExpectedType(typeCast, false))) {
          return !TypeConversionUtil.isSafeConversion(castType, opType); // let's suppose that casts losing precision are important
        } else {
          return !castType.equals(opType); // cast might be necessary (e.g. ((double)1)/5)
        }
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
      if (PsiUtil.skipParenthesizedExprDown(((PsiConditionalExpression)stripParenthesisOperand).getThenExpression()) instanceof PsiFunctionalExpression || 
          PsiUtil.skipParenthesizedExprDown(((PsiConditionalExpression)stripParenthesisOperand).getElseExpression()) instanceof PsiFunctionalExpression) {
        return true;
      }
    }

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(typeCast.getParent());

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
    else if (parent instanceof PsiLocalVariable) {
      return ((PsiLocalVariable)parent).getTypeElement().isInferredType();
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
    final PsiElement exprList = PsiUtil.skipParenthesizedExprUp(typeCast.getParent());
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
           AnnotationUtil.isAnnotated((PsiMethod)method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0);
  }
}