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
import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.PurityInference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;

public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  private final Project myProject;

  public InferredAnnotationsManagerImpl(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    listOwner = BaseExternalAnnotationsManager.preferCompiledElement(listOwner);

    if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && listOwner instanceof PsiMethod) {
      PsiAnnotation anno = getHardcodedContractAnnotation((PsiMethod)listOwner);
      if (anno != null) {
        return anno;
      }
    }

    if (!ignoreBytecodeInference(listOwner, annotationFQN)) {
      PsiAnnotation fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotation(listOwner, annotationFQN);
      if (fromBytecode != null) {
        return fromBytecode;
      }
    }

    if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && canHaveContract(listOwner)) {
      return getInferredContractAnnotation((PsiMethod)listOwner);
    }

    return null;
  }

  private PsiAnnotation getHardcodedContractAnnotation(PsiMethod method) {
    List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, null);
    return contracts.isEmpty() ? null : createContractAnnotation(contracts, HardcodedContracts.isHardcodedPure(method));
  }

  private static boolean ignoreBytecodeInference(PsiModifierListOwner owner, String annotationFQN) {
    if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && hasHardcodedContracts(owner)) {
      return true;
    }
    if (AnnotationUtil.NOT_NULL.equals(annotationFQN) &&
        owner instanceof PsiParameter && owner.getParent() != null &&
        hasHardcodedContracts(owner.getParent().getParent())) {
      return true;
    }
    return false;
  }

  private static boolean hasHardcodedContracts(PsiElement owner) {
    return owner instanceof PsiMethod && !HardcodedContracts.getHardcodedContracts((PsiMethod)owner, null).isEmpty();
  }

  @Nullable
  private PsiAnnotation getInferredContractAnnotation(PsiMethod method) {
    if (method.getModifierList().findAnnotation(ORG_JETBRAINS_ANNOTATIONS_CONTRACT) != null) {
      return null;
    }

    return createContractAnnotation(ContractInference.inferContracts(method), PurityInference.inferPurity(method));
  }

  @Nullable
  private PsiAnnotation createContractAnnotation(List<MethodContract> contracts, boolean pure) {
    final String attrs;
    if (!contracts.isEmpty() && pure) {
      attrs = "value = " + "\"" + StringUtil.join(contracts, "; ") + "\", pure = true";
    } else if (pure) {
      attrs = "pure = true";
    } else if (!contracts.isEmpty()) {
      attrs = "\"" + StringUtil.join(contracts, "; ") + "\"";
    } else {
      return null;
    }
    return ProjectBytecodeAnalysis.getInstance(myProject).createContractAnnotation(attrs);
  }

  private static boolean canHaveContract(PsiModifierListOwner listOwner) {
    return listOwner instanceof PsiMethod && !PsiUtil.canBeOverriden((PsiMethod)listOwner);
  }

  @NotNull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    listOwner = BaseExternalAnnotationsManager.preferCompiledElement(listOwner);
    List<PsiAnnotation> result = ContainerUtil.newArrayList();
    PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotations(listOwner);
    for (PsiAnnotation annotation : fromBytecode) {
      if (!ignoreBytecodeInference(listOwner, annotation.getQualifiedName())) {
        if (!ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotation.getQualifiedName()) || canHaveContract(listOwner)) {
          result.add(annotation);
        }
      }
    }

    if (canHaveContract(listOwner)) {
      PsiAnnotation hardcoded = getHardcodedContractAnnotation((PsiMethod)listOwner);
      ContainerUtil.addIfNotNull(result, hardcoded != null ? hardcoded : getInferredContractAnnotation((PsiMethod)listOwner));
    }

    return result.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
