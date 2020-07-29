// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.RecaptureTypeMapper;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.siyeh.ig.bugs.NullArgumentToVariableArgMethodInspection;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class RedundantCastUtil {
  private static final Logger LOG = Logger.getInstance(RedundantCastUtil.class);

  private RedundantCastUtil() { }

  @NotNull
  public static List<PsiTypeCastExpression> getRedundantCastsInside(@NotNull PsiElement where) {
    HashSet<PsiTypeCastExpression> casts = new HashSet<>();
    JavaElementVisitor visitor = createRedundantCastVisitor(new CommonProcessors.CollectProcessor<>(casts));
    where.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        element.accept(visitor);
      }
    });
    return new ArrayList<>(casts);
  }

  public static boolean isCastRedundant(PsiTypeCastExpression typeCast) {
    PsiElement parent = typeCast.getParent();
    PsiExpression operand = typeCast.getOperand();
    if (operand != null && operand.getType() != null && operand.getType().equals(typeCast.getType())) return true;
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
    if (parent instanceof PsiExpressionList) parent = parent.getParent();
    if (parent instanceof PsiReferenceExpression) parent = parent.getParent();
    if (parent instanceof PsiAnonymousClass) parent = parent.getParent();

    HashSet<PsiTypeCastExpression> casts = new HashSet<>();
    JavaElementVisitor visitor = createRedundantCastVisitor(new CommonProcessors.CollectProcessor<>(casts));
    parent.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        element.accept(visitor);
        if (!casts.isEmpty()) {
          stopWalking();
        }
      }
    });
    return casts.contains(typeCast);
  }

  public static JavaElementVisitor createRedundantCastVisitor(Processor<? super PsiTypeCastExpression> processor) {
    return new MyIsRedundantVisitor() {
      @Override
      protected void registerCast(@NotNull PsiTypeCastExpression typeCast) {
        processor.process(typeCast);
      }
    };
  }

  @Nullable
  private static PsiExpression deparenthesizeExpression(PsiExpression arg) {
    return PsiUtil.skipParenthesizedExprDown(arg);
  }

  private static abstract class MyIsRedundantVisitor extends JavaElementVisitor {
    private void addToResults(@NotNull PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantic(typeCast)) {
        registerCast(typeCast);
      }
    }

    private void addIfNarrowing(PsiTypeCastExpression castExpression, @NotNull PsiType opType, PsiType expectedTypeByParent) {
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
          PsiType opType = operand.getType();
          if (opType != null) {
            addIfNarrowing((PsiTypeCastExpression)expression, opType, expectedTypeByParent);
          }
        }
      }
    }

    protected abstract void registerCast(@NotNull PsiTypeCastExpression typeCast);

    @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      processTypeCastWithExpectedType(expression.getRExpression(), expression.getLExpression().getType());
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      PsiType type = expression.getType();
      if (type instanceof PsiArrayType) {
        for (PsiExpression initializer : expression.getInitializers()) {
          processTypeCastWithExpectedType(initializer, ((PsiArrayType)type).getComponentType());
        }
      }
      super.visitArrayInitializerExpression(expression);
    }

    @Override public void visitVariable(PsiVariable variable) {
      processTypeCastWithExpectedType(variable.getInitializer(), variable.getType());
      super.visitVariable(variable);
    }

    @Override public void visitReturnStatement(PsiReturnStatement statement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method != null) {
        final PsiType returnType = method.getReturnType();
        final PsiExpression returnValue = statement.getReturnValue();
        if (returnValue != null) {
          processTypeCastWithExpectedType(returnValue, returnType);
        }
      }
      super.visitReturnStatement(statement);
    }

    private void processTypeCastWithExpectedType(PsiExpression rExpr, @Nullable PsiType lType) {
      rExpr = deparenthesizeExpression(rExpr);
      if (rExpr instanceof PsiTypeCastExpression) {
        PsiExpression castOperand = getInnerMostOperand(rExpr);
        if (castOperand != null && lType != null) {
          PsiType opType = getOpTypeWithExpected(castOperand, lType);
          if (opType != null) {
            if (castOperand instanceof PsiConditionalExpression) {
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)castOperand).getThenExpression())) return;
              if (!isApplicableForConditionalBranch(opType, ((PsiConditionalExpression)castOperand).getElseExpression())) return;
            }
            if (TypeConversionUtil.isAssignable(lType, opType, false)) {
              if (!isFunctionalExpressionTypePreserved((PsiTypeCastExpression)rExpr, castOperand, lType)) return;
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    private static boolean isFunctionalExpressionTypePreserved(PsiTypeCastExpression typeCast,
                                                               PsiExpression castOperand,
                                                               @NotNull PsiType lType) {
      if (castOperand instanceof PsiFunctionalExpression) {
        final PsiTypeElement typeElement = typeCast.getCastType();
        final PsiType castType = typeElement != null ? typeElement.getType() : null;
        return lType.equals(castType);
      }
      return true;
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      class Processor {
        private @Nullable PsiType processBinaryExpressionOperand(final PsiExpression operand,
                                                                 final PsiType otherType,
                                                                 final IElementType binaryToken) {
          if (operand instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
            PsiExpression toCast = typeCast.getOperand();
            if (toCast != null && otherType != null && 
                TypeConversionUtil.isBinaryOperatorApplicable(binaryToken, toCast.getType(), otherType, false)) {
              addToResults(typeCast);
              return toCast.getType();
            }
          }
          return operand != null ? operand.getType() : null;
        }
      }
      
      Processor processor = new Processor();
      IElementType tokenType = expression.getOperationTokenType();
      PsiExpression[] operands = expression.getOperands();
      if (operands.length >= 2) {
        PsiType lType = processor.processBinaryExpressionOperand(deparenthesizeExpression(operands[0]), operands[1].getType(), tokenType);
        
        for (int i = 1; i < operands.length; i++) {
          PsiExpression operand = deparenthesizeExpression(operands[i]);
          if (operand == null) continue;
          PsiType rType = processor.processBinaryExpressionOperand(operand, lType, tokenType);
          lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, tokenType, true);
        }
      }
      super.visitPolyadicExpression(expression);
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      processCall(callExpression);
      super.visitCallExpression(callExpression);
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
        if (parent instanceof PsiMethodCallExpression) {//check for virtual call
          PsiMethod targetMethod = (PsiMethod)oldMember;
          if (targetMethod.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
          }
          PsiMethodCallExpression newCall =
            (PsiMethodCallExpression)copyCallExpression(((PsiMethodCallExpression)parent), PsiTypesUtil.getExpectedTypeByParent(parent));
          if (newCall == null) return false;
          PsiExpression newQualifier = deparenthesizeExpression(newCall.getMethodExpression().getQualifierExpression());
          LOG.assertTrue(newQualifier != null);
          PsiElement oldReference = newQualifier.getCopyableUserData(RecaptureTypeMapper.SELF_REFERENCE);
          PsiElement replace = newQualifier.replace(getInnerMostOperand(newQualifier));
          replace.putCopyableUserData(RecaptureTypeMapper.SELF_REFERENCE, oldReference);

          final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
          if (!newResult.isValidResult()) return false;

          final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
          LOG.assertTrue(newTargetMethod != null, "isValidResult() check above should find this");

          PsiType newReturnType = newCall.getType();
          PsiType oldReturnType = ((PsiMethodCallExpression)parent).getType();

          if (Comparing.equal(newReturnType == null ? null : new RecaptureTypeMapper().mapType(newReturnType), oldReturnType) &&
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
      if (args.length == 0) return;
      final JavaResolveResult oldResult = expression.resolveMethodGenerics();
      final PsiElement element = oldResult.getElement();
      if (!(element instanceof PsiMethod)) return;
      PsiMethod oldMethod = (PsiMethod)element;
      PsiParameter[] parameters = oldMethod.getParameterList().getParameters();

      PsiCall newCall = null;
      for (int i = 0; i < args.length; i++) {
        final PsiExpression arg = deparenthesizeExpression(args[i]);
        if (!(arg instanceof PsiTypeCastExpression) && !(arg instanceof PsiLambdaExpression) && !(arg instanceof PsiConditionalExpression)) {
          continue;
        }

        ProgressManager.checkCanceled();
        if (newCall == null) {
          newCall = copyCallExpression(expression, PsiTypesUtil.getExpectedTypeByParent(expression));
        }
        if (newCall == null) return;
        
        final PsiExpressionList argList = newCall.getArgumentList();
        LOG.assertTrue(argList != null);
        PsiExpression[] newArgs = argList.getExpressions();
        LOG.assertTrue(newArgs.length == args.length, "oldCall: " + expression.getText() + "; old length: " + args.length + "; newCall: " + newCall.getText() + "; new length: " + newArgs.length);

        if (arg instanceof PsiTypeCastExpression) {
          checkTypeCastInCallArgument(i, (PsiTypeCastExpression)arg, newArgs, expression, newCall);
        }
        else if (arg instanceof PsiLambdaExpression) {
          checkLambdaReturnsInsideCall(i, (PsiLambdaExpression)arg, newArgs, expression, newCall, parameters);
        }
        else {
          PsiType conditionalType = arg.getType();
          
          PsiExpression thenExpression = deparenthesizeExpression(((PsiConditionalExpression)arg).getThenExpression());
          PsiExpression elseExpression = deparenthesizeExpression(((PsiConditionalExpression)arg).getElseExpression());

          if (thenExpression instanceof PsiTypeCastExpression &&
              !castForBoxing(getInnerMostOperand(thenExpression), elseExpression != null ? elseExpression.getType() : null, conditionalType)) {
            PsiExpression newThenExpression = deparenthesizeExpression(((PsiConditionalExpression)Objects.requireNonNull(deparenthesizeExpression(newArgs[i]))).getThenExpression());
            checkConditionalBranch(expression, newCall, thenExpression, newThenExpression);
          }
          if (elseExpression instanceof PsiTypeCastExpression &&
              !castForBoxing(getInnerMostOperand(elseExpression), thenExpression != null ? thenExpression.getType() : null, conditionalType)) {
            PsiExpression newElseExpression = deparenthesizeExpression(((PsiConditionalExpression)Objects.requireNonNull(deparenthesizeExpression(newArgs[i]))).getElseExpression());
            checkConditionalBranch(expression, newCall, elseExpression, newElseExpression);
          }
        }
      }
    }

    private void checkConditionalBranch(PsiCall oldCall,
                                        PsiCall newCall,
                                        PsiExpression oldBranchExpression,
                                        PsiExpression newBranchExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)newBranchExpression).getOperand();
      if (operand != null) {
        newBranchExpression = (PsiExpression)newBranchExpression.replace(operand);
        JavaResolveResult oldResult = oldCall.resolveMethodGenerics();
        JavaResolveResult newResult = resolveNewResult(oldCall, newCall);
        
        if (isSameResolveResult(oldResult, newResult)) {
          addToResults((PsiTypeCastExpression)oldBranchExpression);
        }
        else {
          newBranchExpression.replace(oldBranchExpression);
        }
      }
    }

    private static boolean isSameResolveResult(JavaResolveResult oldResult, JavaResolveResult newResult) {
      PsiMethod oldMethod = (PsiMethod)oldResult.getElement();
      LOG.assertTrue(oldMethod != null);
      return oldMethod.equals(newResult.getElement()) &&
             newResult.isValidResult() &&
             !(newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).getInferenceErrorMessage() != null) &&
             new RecaptureTypeMapper().recapture(newResult.getSubstitutor()).equals(oldResult.getSubstitutor());
    }

    private static boolean castForBoxing(PsiExpression operand, PsiType oppositeType, PsiType conditionalType) {
      return operand != null &&
             TypeConversionUtil.isPrimitiveAndNotNull(operand.getType()) &&
             !(oppositeType instanceof PsiPrimitiveType) &&
             !(conditionalType instanceof PsiPrimitiveType);
    }

    private void checkLambdaReturnsInsideCall(int i,
                                              PsiLambdaExpression arg,
                                              PsiExpression[] newArgs,
                                              PsiCall oldCall,
                                              PsiCall newCall,
                                              PsiParameter[] parameters) {
      final PsiType interfaceType = arg.getFunctionalInterfaceType();
      if (interfaceType != null) {
        List<PsiExpression> expressions = LambdaUtil.getReturnExpressions(arg);
        PsiLambdaExpression newLambdaExpression = (PsiLambdaExpression)deparenthesizeExpression(newArgs[i]);
        LOG.assertTrue(newLambdaExpression != null);
        List<PsiExpression> newReturnExpressions = LambdaUtil.getReturnExpressions(newLambdaExpression);

        for (int returnExprIdx = 0; returnExprIdx < expressions.size(); returnExprIdx++) {
          ProgressManager.checkCanceled();
          PsiExpression returnExpression = deparenthesizeExpression(expressions.get(returnExprIdx));
          final PsiExpression newReturnExpression = deparenthesizeExpression(newReturnExpressions.get(returnExprIdx));
          if (newReturnExpression instanceof PsiTypeCastExpression) {
            checkLambdaReturn(i, (PsiTypeCastExpression)returnExpression, (PsiTypeCastExpression)newReturnExpression, interfaceType, newLambdaExpression, oldCall, newCall, parameters);
          }
          else if (returnExpression instanceof PsiConditionalExpression) {
            LOG.assertTrue(newReturnExpression instanceof PsiConditionalExpression);
            PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)returnExpression;
            PsiConditionalExpression newConditionalExpression = (PsiConditionalExpression)newReturnExpression;
            
            final PsiExpression thenExpression = deparenthesizeExpression(conditionalExpression.getThenExpression());
            final PsiExpression newThenExpression = deparenthesizeExpression(newConditionalExpression.getThenExpression());

            final PsiExpression elseExpression = deparenthesizeExpression(conditionalExpression.getElseExpression());
            final PsiExpression newElseExpression = deparenthesizeExpression(newConditionalExpression.getElseExpression());

            if (thenExpression instanceof PsiTypeCastExpression &&
                !castForBoxing(getInnerMostOperand(thenExpression), elseExpression != null ? elseExpression.getType() : null, returnExpression.getType())) {
              checkLambdaReturn(i, (PsiTypeCastExpression)thenExpression, (PsiTypeCastExpression)newThenExpression, interfaceType, newLambdaExpression, oldCall, newCall, parameters);
            }

            if (elseExpression instanceof PsiTypeCastExpression &&
                !castForBoxing(getInnerMostOperand(elseExpression), thenExpression != null ? thenExpression.getType() : null, returnExpression.getType())) {
              checkLambdaReturn(i, (PsiTypeCastExpression)elseExpression, (PsiTypeCastExpression)newElseExpression, interfaceType, newLambdaExpression, oldCall, newCall, parameters);
            }
          }
        }
      }
    }

    private void checkLambdaReturn(int i,
                                   PsiTypeCastExpression returnExpression,
                                   PsiTypeCastExpression newReturnExpression,
                                   PsiType originalFunctionalInterfaceType,
                                   PsiLambdaExpression newLambdaExpression, 
                                   PsiCall oldCall,
                                   PsiCall newCall,
                                   PsiParameter[] parameters) {
      PsiExpression castOperand = getInnerMostOperand(returnExpression);
      if (castOperand == null) return;
      final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(originalFunctionalInterfaceType);
      if (interfaceReturnType == null || 
          !isFunctionalExpressionTypePreserved(returnExpression, castOperand, interfaceReturnType)) return;
      PsiExpression strippedCast = (PsiExpression)newReturnExpression.replace(castOperand);
      PsiType newArgType = calculateNewArgType(i, resolveNewResult(oldCall, newCall), parameters);
      final PsiType functionalInterfaceType = newLambdaExpression.getGroundTargetType(newArgType);
      if (originalFunctionalInterfaceType.equals(functionalInterfaceType)) {
        final PsiType castExprType = LambdaUtil.performWithTargetType(newLambdaExpression, functionalInterfaceType, () -> strippedCast.getType());
        if (castExprType != null && interfaceReturnType.isAssignableFrom(castExprType)) {
          addToResults(returnExpression);
          return;
        }
      }
      strippedCast.replace(returnExpression);
    }
    
    private void checkTypeCastInCallArgument(int i, 
                                             PsiTypeCastExpression arg,
                                             PsiExpression[] newArgs,
                                             PsiCall oldCall,
                                             PsiCall newCall) {
      final PsiTypeElement castTypeElement = arg.getCastType();
      final PsiType castType = castTypeElement != null ? castTypeElement.getType() : null;

      PsiExpression castOperand = ((PsiTypeCastExpression)Objects.requireNonNull(deparenthesizeExpression(newArgs[i]))).getOperand();
      if (castOperand == null) return;
      newArgs[i] = (PsiExpression)newArgs[i].replace(castOperand);

      JavaResolveResult oldResult = oldCall.resolveMethodGenerics();

      final JavaResolveResult newResult = resolveNewResult(oldCall, newCall);

      PsiMethod oldMethod = (PsiMethod)oldResult.getElement();
      LOG.assertTrue(oldMethod != null);
      PsiParameter[] parameters = oldMethod.getParameterList().getParameters();

      if (i == newArgs.length - 1 && newArgs.length == parameters.length && parameters[i].isVarArgs() &&
          (NullArgumentToVariableArgMethodInspection.isSuspiciousVararg(newCall, newArgs[i].getType(), () -> (PsiMethod)newResult.getElement()) ||
           oldResult instanceof MethodCandidateInfo && newResult instanceof MethodCandidateInfo &&
           ((MethodCandidateInfo)oldResult).getApplicabilityLevel() != ((MethodCandidateInfo)newResult).getApplicabilityLevel())) {
        newArgs[i].replace(arg);
        return;
      }

      if (isSameResolveResult(oldResult, newResult)) {
        PsiExpression newArg = PsiUtil.deparenthesizeExpression(newArgs[i]);
        if (newArg instanceof PsiFunctionalExpression) {
          PsiType newArgType = calculateNewArgType(i, newResult, parameters);

          if (Comparing.equal(castType, ((PsiFunctionalExpression)newArg).getGroundTargetType(newArgType))) {
            addToResults(arg);
            return;
          }
        }
        else if (newArg instanceof PsiCallExpression) { 
          //if top method is not generic, all inference problems may be collected in the argument itself
          JavaResolveResult result = ((PsiCallExpression)newArg).resolveMethodGenerics();
          if (!(result instanceof MethodCandidateInfo) || ((MethodCandidateInfo)result).getInferenceErrorMessage() == null) {
            addToResults(arg);
            return;
          }
        }
        else {
          addToResults(arg);
          return;
        }
      }
      newArgs[i].replace(arg);
    }

    @Nullable
    private static PsiType calculateNewArgType(int i, JavaResolveResult newResult, PsiParameter[] parameters) {
      final boolean varargs = newResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)newResult).isVarargs();
      final PsiType parameterType = PsiTypesUtil.getParameterType(parameters, i, varargs);
      PsiType newArgType = newResult.getSubstitutor().substitute(parameterType);

      if (newResult instanceof MethodCandidateInfo && PsiUtil.isRawSubstitutor(((MethodCandidateInfo)newResult).getElement(), newResult.getSubstitutor())) {
        newArgType = TypeConversionUtil.erasure(newArgType);
      }
      return newArgType;
    }

    @NotNull
    private static JavaResolveResult resolveNewResult(PsiCall oldCall, PsiCall newCall) {
      final JavaResolveResult newResult;
      if (newCall instanceof PsiEnumConstant) {
        // do this manually, because PsiEnumConstantImpl.resolveMethodGenerics() will assert (no containing class for the copy)
        final PsiEnumConstant enumConstant = (PsiEnumConstant)oldCall;
        PsiClass containingClass = enumConstant.getContainingClass();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(enumConstant.getProject());
        final PsiClassType type = facade.getElementFactory().createType(Objects.requireNonNull(containingClass));
        newResult = facade.getResolveHelper().resolveConstructor(type, Objects.requireNonNull(newCall.getArgumentList()), enumConstant);
      }
      else {
        newResult = newCall.resolveMethodGenerics();
      }
      return newResult;
    }

    @Nullable
    private static PsiCall copyCallExpression(PsiCall expression, PsiType typeByParent) {
      PsiElement encoded = null;
      try {
        if (typeByParent != null && PsiTypesUtil.isDenotableType(typeByParent, expression)) {
          RecaptureTypeMapper.encode(encoded = expression);
          return  (PsiCall)LambdaUtil.copyWithExpectedType(expression, typeByParent);
        }
        else {
          final PsiCall call = LambdaUtil.treeWalkUp(expression);
          if (call != null) {
            RecaptureTypeMapper.encode(encoded = call);
            Object marker = new Object();
            PsiTreeUtil.mark(expression, marker);
            final PsiCall callCopy = LambdaUtil.copyTopLevelCall(call);
            if (callCopy == null) return null;
            return  (PsiCall)PsiTreeUtil.releaseMark(callCopy, marker);
          }
          else {
            RecaptureTypeMapper.encode(encoded = expression);
            return (PsiCall)expression.copy();
          }
        }
      }
      catch (IllegalArgumentException e) {
        return null;
      }
      finally {
        if (encoded != null) {
          RecaptureTypeMapper.clean(encoded);
        }
      }
    }

    //nested type casts only
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
          if (opType != null) {
            addIfNarrowing((PsiTypeCastExpression)switchVariable, opType, null);
          }
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

      if (!(gParent instanceof PsiExpressionList)) {
        PsiExpression thenExpression = deparenthesizeExpression(conditionalExpression.getThenExpression());
        PsiExpression elseExpression = deparenthesizeExpression(conditionalExpression.getElseExpression());
        if (thenExpression instanceof PsiTypeCastExpression) {
          visitConditional((PsiTypeCastExpression)thenExpression,
                           conditionalExpression,
                           getInnerMostOperand(elseExpression));
        }
        if (elseExpression instanceof PsiTypeCastExpression) {
          visitConditional((PsiTypeCastExpression)elseExpression, 
                           conditionalExpression, 
                           getInnerMostOperand(deparenthesizeExpression(conditionalExpression.getThenExpression())));
        }
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
      if (opType == null) return;
      
      if (castTo instanceof PsiClassType && TypeConversionUtil.isPrimitiveAndNotNull(opType)) {
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
        if (!PsiPolyExpressionUtil.isPolyExpression(parent)) {    //branches need to be of the same type
          if (oppositeOperand == null || !Comparing.equal(conditionalType, oppositeOperand.getType())) return;
        }
      }
      addIfNarrowing(typeCast, opType, null);
    }

    private static PsiType getOpTypeWithExpected(PsiExpression operand, PsiType expectedTypeByParent) {
      PsiType opType = operand.getType();

      if (expectedTypeByParent != null && !(operand instanceof PsiFunctionalExpression)) {
        try {
          final PsiExpression initializer = (PsiExpression)LambdaUtil.copyWithExpectedType(operand, expectedTypeByParent);
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
}