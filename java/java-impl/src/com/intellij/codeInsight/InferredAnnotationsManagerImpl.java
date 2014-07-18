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
package com.intellij.codeInsight;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.codeInspection.dataFlow.ContractInference;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  @Nullable
  @Override
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    PsiAnnotation fromBytecode = ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).findInferredAnnotation(listOwner, annotationFQN);
    if (fromBytecode != null) {
      return fromBytecode;
    }

    if (listOwner instanceof PsiMethod && ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN)) {
      List<MethodContract> contracts = ContractInference.inferContracts((PsiMethod)listOwner);
      if (!contracts.isEmpty()) {
        return ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).createContractAnnotation("\"" + StringUtil.join(contracts, "; ") + "\"");
      }
    }

    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).findInferredAnnotations(listOwner);
    if (fromBytecode.length > 0) {
      return fromBytecode;
    }

    if (listOwner instanceof PsiMethod) {
      List<MethodContract> contracts = ContractInference.inferContracts((PsiMethod)listOwner);
      if (!contracts.isEmpty()) {
        return new PsiAnnotation[]{ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).createContractAnnotation("\"" + StringUtil.join(contracts, "; ") + "\"")};
      }
    }

    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
