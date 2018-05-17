// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ParseException;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.parseContract;

/**
 * @author peter
 */
public class ContractInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethod(PsiMethod method) {
        PsiAnnotation annotation = JavaMethodContractUtil.findContractAnnotation(method);
        if (annotation == null || (!ApplicationManager.getApplication().isInternal() && AnnotationUtil.isInferredAnnotation(annotation))) {
          return;
        }
        boolean ownContract = annotation.getOwner() == method.getModifierList();
        for (StandardMethodContract contract : JavaMethodContractUtil.getMethodContracts(method)) {
          Map<PsiElement, String> errors = ContractChecker.checkContractClause(method, contract, ownContract);
          for (Map.Entry<PsiElement, String> entry : errors.entrySet()) {
            PsiElement element = entry.getKey();
            holder.registerProblem(element, entry.getValue());
          }
        }
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
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
            error = "Pure method cannot have mutation contract";
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
    List<Conditions> possibleConditions = Collections.singletonList(new Conditions(paramCount));
    for (int clauseIndex = 0; clauseIndex < contracts.size(); clauseIndex++) {
      StandardMethodContract contract = contracts.get(clauseIndex);
      if (contract.getParameterCount() != paramCount) {
        return ParseException.forClause("Method takes " + paramCount + " parameters, " +
                                        "while contract clause '" + contract + "' expects " + contract.getParameterCount(), text,
                                        clauseIndex);
      }
      for (int i = 0; i < parameters.length; i++) {
        ValueConstraint constraint = contract.getParameterConstraint(i);
        PsiType type = parameters[i].getType();
        switch (constraint) {
          case ANY_VALUE:
            break;
          case NULL_VALUE:
          case NOT_NULL_VALUE:
            if (type instanceof PsiPrimitiveType) {
              String message =
                "Contract clause '" + contract + "': parameter #" + (i + 1) + " has primitive type '" + type.getPresentableText() + "'";
              return ParseException.forConstraint(message, text, clauseIndex, i);
            }
            break;
          case TRUE_VALUE:
          case FALSE_VALUE:
            if (!PsiType.BOOLEAN.equals(type) && !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
              String message = "Contract clause '" + contract + "': parameter #" + (i + 1) + " has '" +
                               type.getPresentableText() + "' type (expected boolean)";
              return ParseException.forConstraint(message, text, clauseIndex, i);
            }
            break;
        }
      }
      String problem = contract.getReturnValue().getMethodCompatibilityProblem(method);
      if (problem != null) {
        return ParseException.forReturnValue(problem, text, clauseIndex);
      }
      if (possibleConditions != null) {
        if (possibleConditions.isEmpty()) {
          return ParseException
            .forClause("Contract clause '" + contract + "' is unreachable: previous contracts cover all possible cases", text, clauseIndex);
        }
        if (StreamEx.of(possibleConditions).allMatch(c -> c.fitContract(contract) == null)) {
          return ParseException.forClause(
            "Contract clause '" + contract + "' is never satisfied as its conditions are covered by previous contracts", text, clauseIndex);
        }
        possibleConditions = StreamEx.of(possibleConditions).flatMap(c -> c.misfitContract(contract))
                                     .limit(DataFlowRunner.MAX_STATES_PER_BRANCH).toList();
        if (possibleConditions.size() >= DataFlowRunner.MAX_STATES_PER_BRANCH) {
          possibleConditions = null;
        }
      }
    }
    return null;
  }

  private static final class Conditions {
    private final List<ValueConstraint> myParameters;

    Conditions(int paramCount) {
      myParameters = StreamEx.constant(ValueConstraint.ANY_VALUE, paramCount).toList();
    }

    private Conditions(List<ValueConstraint> parameters) {
      myParameters = parameters;
    }

    @Nullable
    Conditions fitContract(StandardMethodContract contract) {
      List<ValueConstraint> result = new ArrayList<>(myParameters);
      assert contract.getParameterCount() == result.size();
      for (int i = 0; i < result.size(); i++) {
        ValueConstraint condition = result.get(i);
        ValueConstraint constraint = contract.getParameterConstraint(i);
        if (condition == constraint || condition == ValueConstraint.ANY_VALUE) {
          result.set(i, constraint);
        } else if (constraint == ValueConstraint.ANY_VALUE) {
          result.set(i, condition);
        }
        else {
          return null;
        }
      }
      return new Conditions(result);
    }

    @NotNull
    Stream<Conditions> misfitContract(StandardMethodContract contract) {
      assert contract.getParameterCount() == myParameters.size();
      List<ValueConstraint> constraints = contract.getConstraints();
      List<ValueConstraint> template = StreamEx.constant(ValueConstraint.ANY_VALUE, myParameters.size()).toList();
      List<StandardMethodContract> antiContracts = new ArrayList<>();
      for (int i = 0; i < constraints.size(); i++) {
        ValueConstraint constraint = constraints.get(i);
        if (constraint == ValueConstraint.ANY_VALUE) continue;
        template.set(i, constraint.negate());
        antiContracts.add(new StandardMethodContract(template.toArray(new ValueConstraint[0]), ContractReturnValue.returnAny()));
        template.set(i, constraint);
      }
      return StreamEx.of(antiContracts).map(this::fitContract).nonNull();
    }

    @Override
    public String toString() {
      return myParameters.toString();
    }
  }
}
