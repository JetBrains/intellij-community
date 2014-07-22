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
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;

public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  @Nullable
  @Override
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    PsiAnnotation fromBytecode = ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).findInferredAnnotation(listOwner, annotationFQN);
    if (fromBytecode != null) {
      return fromBytecode;
    }

    if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && canHaveContract(listOwner)) {
      List<MethodContract> contracts = ContractInference.inferContracts((PsiMethod)listOwner);
      if (!contracts.isEmpty()) {
        return ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).createContractAnnotation("\"" + StringUtil.join(contracts, "; ") + "\"");
      }
    }

    return null;
  }

  private static boolean canHaveContract(PsiModifierListOwner listOwner) {
    return listOwner instanceof PsiMethod && !PsiUtil.canBeOverriden((PsiMethod)listOwner);
  }

  @NotNull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    List<PsiAnnotation> result = ContainerUtil.newArrayList();
    PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(listOwner.getProject()).findInferredAnnotations(listOwner);
    for (PsiAnnotation annotation : fromBytecode) {
      if (!ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotation.getQualifiedName()) || canHaveContract(listOwner)) {
        result.add(annotation);
      }
    }

    if (canHaveContract(listOwner)) {
      List<MethodContract> contracts = ContractInference.inferContracts((PsiMethod)listOwner);
      if (!contracts.isEmpty()) {
        result.add(ProjectBytecodeAnalysis.getInstance(listOwner.getProject())
                     .createContractAnnotation("\"" + StringUtil.join(contracts, "; ") + "\""));
      }
    }

    return result.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
