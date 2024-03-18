// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ParseException;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.parseContract;

public final class ContractInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        PsiAnnotation annotation = JavaMethodContractUtil.findContractAnnotation(method);
        if (annotation == null || (!ApplicationManager.getApplication().isInternal() && AnnotationUtil.isInferredAnnotation(annotation))) {
          return;
        }
        boolean ownContract = annotation.getOwner() == method.getModifierList();
        for (StandardMethodContract contract : JavaMethodContractUtil.getMethodContracts(method)) {
          Map<PsiElement, String> errors = ContractChecker.checkContractClause(method, contract, ownContract);
          errors.forEach(holder::registerProblem);
        }
      }

      @Override
      public void visitAnnotation(@NotNull PsiAnnotation annotation) {
        if (!JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotation.getQualifiedName())) return;

        PsiMethod method = PsiTreeUtil.getParentOfType(annotation, PsiMethod.class);
        if (method == null) return;

        String text = AnnotationUtil.getStringAttributeValue(annotation, null);
        if (StringUtil.isNotEmpty(text)) {
          ParseException error = checkContract(method, text);
          if (error != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(null);
            assert value != null;
            TextRange actualRange = null;
            if (value instanceof PsiExpression && error.getRange() != null) {
              actualRange = ExpressionUtils
                .findStringLiteralRange((PsiExpression)value, error.getRange().getStartOffset(), error.getRange().getEndOffset());
            }
            holder.registerProblem(value, actualRange, error.getMessage());
          }
        }
        checkMutationContract(annotation, method);
      }

      private void checkMutationContract(PsiAnnotation annotation, PsiMethod method) {
        String mutationContract = AnnotationUtil.getStringAttributeValue(annotation, MutationSignature.ATTR_MUTATES);
        if (StringUtil.isNotEmpty(mutationContract)) {
          boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "pure"));
          String error;
          if (pure) {
            error = JavaAnalysisBundle.message("inspection.contract.checker.pure.method.mutation.contract");
          } else {
            error = MutationSignature.checkSignature(mutationContract, method);
          }
          if (error != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(MutationSignature.ATTR_MUTATES);
            assert value != null;
            holder.registerProblem(value, error);
          }
        }
      }
    };
  }

  @Nullable
  public static ParseException checkContract(PsiMethod method, String text) {
    List<StandardMethodContract> contracts;
    try {
      contracts = parseContract(text);
    }
    catch (ParseException e) {
      return e;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int paramCount = parameters.length;
    List<StandardMethodContract> possibleContracts =
      Collections.singletonList(StandardMethodContract.trivialContract(paramCount, ContractReturnValue.returnAny()));
    for (int clauseIndex = 0; clauseIndex < contracts.size(); clauseIndex++) {
      StandardMethodContract contract = contracts.get(clauseIndex);
      if (contract.getParameterCount() != paramCount) {
        String message = JavaAnalysisBundle
          .message("inspection.contract.checker.parameter.count.mismatch", paramCount, contract, contract.getParameterCount());
        return ParseException.forClause(message, text, clauseIndex);
      }
      for (int i = 0; i < parameters.length; i++) {
        ValueConstraint constraint = contract.getParameterConstraint(i);
        PsiParameter parameter = parameters[i];
        String message = getConstraintProblem(method, contract, constraint, parameter);
        if (message != null) {
          return ParseException.forConstraint(message, text, clauseIndex, i);
        }
      }
      String problem = contract.getReturnValue().getMethodCompatibilityProblem(method);
      if (problem != null) {
        return ParseException.forReturnValue(problem, text, clauseIndex);
      }
      if (possibleContracts != null) {
        if (possibleContracts.isEmpty()) {
          return ParseException
            .forClause(JavaAnalysisBundle.message("inspection.contract.checker.unreachable.contract.clause", contract), text, clauseIndex);
        }
        if (ContainerUtil.and(possibleContracts, c -> c.intersect(contract) == null)) {
          return ParseException.forClause(
            JavaAnalysisBundle.message("inspection.contract.checker.contract.clause.never.satisfied", contract), text, clauseIndex);
        }
        possibleContracts = StreamEx.of(possibleContracts).flatMap(c -> c.excludeContract(contract))
                                     .limit(StandardDataFlowInterpreter.DEFAULT_MAX_STATES_PER_BRANCH).toList();
        if (possibleContracts.size() >= StandardDataFlowInterpreter.DEFAULT_MAX_STATES_PER_BRANCH) {
          possibleContracts = null;
        }
      }
    }
    return null;
  }

  @Nullable
  private static @InspectionMessage String getConstraintProblem(PsiMethod method,
                                                                StandardMethodContract contract,
                                                                ValueConstraint constraint, PsiParameter parameter) {
    PsiType type = parameter.getType();
    switch (constraint) {
      case NULL_VALUE -> {
        if (type instanceof PsiPrimitiveType) {
          return JavaAnalysisBundle.message("inspection.contract.checker.primitive.parameter.nullability",
                                            parameter.getName(), type.getPresentableText(), constraint);
        }
        NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findEffectiveNullabilityInfo(parameter);
        if (info != null && info.getNullability() == Nullability.NOT_NULL) {
          if (info.isInferred() && contract.getReturnValue().isFail()) {
            // null -> fail is ok if not-null was inferred
            return null;
          }
          return JavaAnalysisBundle.message(info.isInferred()
                                            ? "inspection.contract.checker.inferred.notnull.parameter.null"
                                            : "inspection.contract.checker.notnull.parameter.null",
                                            parameter.getName(), constraint);
        }
        return null;
      }
      case NOT_NULL_VALUE -> {
        if (type instanceof PsiPrimitiveType) {
          return JavaAnalysisBundle.message("inspection.contract.checker.primitive.parameter.nullability",
                                            parameter.getName(), type.getPresentableText(), constraint);
        }
        NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findEffectiveNullabilityInfo(parameter);
        if (info != null && info.getNullability() == Nullability.NOT_NULL) {
          return JavaAnalysisBundle.message(info.isInferred()
                                            ? "inspection.contract.checker.inferred.notnull.parameter.notnull"
                                            : "inspection.contract.checker.notnull.parameter.notnull",
                                            parameter.getName(), constraint);
        }
        return null;
      }
      case TRUE_VALUE, FALSE_VALUE -> {
        if (!PsiTypes.booleanType().equals(type) && !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
          return JavaAnalysisBundle.message("inspection.contract.checker.boolean.condition.for.nonboolean.parameter",
                                            parameter.getName(), type.getPresentableText());
        }
        return null;
      }
      default -> {
        return null;
      }
    }
  }
}
