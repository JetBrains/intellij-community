/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
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
public class ContractInspection extends BaseJavaBatchLocalInspectionTool {

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethod(PsiMethod method) {
        for (MethodContract contract : ControlFlowAnalyzer.getMethodContracts(method)) {
          Map<PsiElement, String> errors = ContractChecker.checkContractClause(method, contract, false, isOnTheFly);
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
            return;
          }
        }

        if (Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "pure")) &&
            PsiType.VOID.equals(method.getReturnType())) {
          PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("pure");
          assert value != null;
          holder.registerProblem(value, "Pure methods must return something, void is not allowed as a return type");
        }
      }
    };
  }

  @Nullable
  public static String checkContract(PsiMethod method, String text) {
    List<MethodContract> contracts;
    try {
      contracts = MethodContract.parseContract(text);
    }
    catch (MethodContract.ParseException e) {
      return e.getMessage();
    }
    int paramCount = method.getParameterList().getParametersCount();
    for (int i = 0; i < contracts.size(); i++) {
      MethodContract contract = contracts.get(i);
      if (contract.arguments.length != paramCount) {
        return "Method takes " + paramCount + " parameters, while contract clause number " + (i + 1) + " expects " + contract.arguments.length;
      }
      PsiType returnType = method.getReturnType();
      if (returnType != null && !InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue)) {
        return "Method returns " + returnType.getPresentableText() + " but the contract specifies " + contract.returnValue;
      }
    }
    return null;
  }
}
