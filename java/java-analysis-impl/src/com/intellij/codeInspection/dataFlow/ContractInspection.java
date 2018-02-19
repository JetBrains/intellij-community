// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
        for (StandardMethodContract contract : ControlFlowAnalyzer.getMethodContracts(method)) {
          Map<PsiElement, String> errors = ContractChecker.checkContractClause(method, contract);
          for (Map.Entry<PsiElement, String> entry : errors.entrySet()) {
            PsiElement element = entry.getKey();
            holder.registerProblem(element, entry.getValue());
          }
        }
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (!ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotation.getQualifiedName())) return;

        PsiMethod method = PsiTreeUtil.getParentOfType(annotation, PsiMethod.class);
        if (method == null) return;

        String text = AnnotationUtil.getStringAttributeValue(annotation, null);
        if (StringUtil.isNotEmpty(text)) {
          String error = checkContract(method, text);
          if (error != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(null);
            assert value != null;
            holder.registerProblem(value, error);
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
  public static String checkContract(PsiMethod method, String text) {
    List<StandardMethodContract> contracts;
    try {
      contracts = StandardMethodContract.parseContract(text);
    }
    catch (StandardMethodContract.ParseException e) {
      return e.getMessage();
    }
    int paramCount = method.getParameterList().getParametersCount();
    for (int i = 0; i < contracts.size(); i++) {
      StandardMethodContract contract = contracts.get(i);
      if (contract.arguments.length != paramCount) {
        return "Method takes " + paramCount + " parameters, while contract clause number " + (i + 1) + " expects " + contract.arguments.length;
      }
      PsiType returnType = method.getReturnType();
      if (returnType != null && !InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue)) {
        return "Method returns " + returnType.getPresentableText() + " but the contract specifies " + contract.returnValue;
      }
      if (method.isConstructor() && contract.returnValue != MethodContract.ValueConstraint.THROW_EXCEPTION) {
        return "Invalid contract return value for a constructor: " + contract.returnValue;
      }
    }
    return null;
  }
}
