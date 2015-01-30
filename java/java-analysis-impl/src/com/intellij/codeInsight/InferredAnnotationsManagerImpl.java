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
import com.intellij.codeInspection.dataFlow.*;
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

    if (ignoreInference(listOwner, annotationFQN)) {
      return null;
    }
    
    PsiAnnotation fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotation(listOwner, annotationFQN);
    if (fromBytecode != null) {
      return fromBytecode;
    }

    if (listOwner instanceof PsiMethod) {
      if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN)) {
        return getInferredContractAnnotation((PsiMethod)listOwner);
      }

      if ((AnnotationUtil.NOT_NULL.equals(annotationFQN) || AnnotationUtil.NULLABLE.equals(annotationFQN))) {
        PsiAnnotation anno = getInferredNullityAnnotation((PsiMethod)listOwner);
        return anno == null ? null : annotationFQN.equals(anno.getQualifiedName()) ? anno : null;
      }
    }

    return null;
  }

  @Nullable
  private PsiAnnotation getHardcodedContractAnnotation(PsiMethod method) {
    List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, null);
    return contracts.isEmpty() ? null : createContractAnnotation(contracts, HardcodedContracts.isHardcodedPure(method));
  }

  @Override
  public boolean ignoreInference(@NotNull PsiModifierListOwner owner, @Nullable String annotationFQN) {
    if (owner instanceof PsiMethod && PsiUtil.canBeOverriden((PsiMethod)owner)) {
      return true;
    }
    if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && HardcodedContracts.hasHardcodedContracts(owner)) {
      return true;
    }
    if (AnnotationUtil.NOT_NULL.equals(annotationFQN) && owner instanceof PsiParameter && owner.getParent() != null) {
      if (AnnotationUtil.isAnnotated(owner, NullableNotNullManager.getInstance(owner.getProject()).getNullables(), false, false)) {
        return true;
      }
      if (HardcodedContracts.hasHardcodedContracts(owner)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private PsiAnnotation getInferredContractAnnotation(PsiMethod method) {
    if (method.getModifierList().findAnnotation(ORG_JETBRAINS_ANNOTATIONS_CONTRACT) != null) {
      return null;
    }

    return createContractAnnotation(ContractInference.inferContracts(method), PurityInference.inferPurity(method));
  }

  @Nullable
  private PsiAnnotation getInferredNullityAnnotation(PsiMethod method) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    if (AnnotationUtil.findAnnotation(method, manager.getNotNulls(), true) != null || AnnotationUtil.findAnnotation(method, manager.getNullables(), true) != null) {
      return null;
    }

    Nullness nullness = NullityInference.inferNullity(method);
    if (nullness == Nullness.NOT_NULL) {
      return ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation();
    }
    if (nullness == Nullness.NULLABLE) {
      return ProjectBytecodeAnalysis.getInstance(myProject).getNullableAnnotation();
    }
    return null;
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

  @NotNull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    listOwner = BaseExternalAnnotationsManager.preferCompiledElement(listOwner);
    List<PsiAnnotation> result = ContainerUtil.newArrayList();
    PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotations(listOwner);
    for (PsiAnnotation annotation : fromBytecode) {
      if (!ignoreInference(listOwner, annotation.getQualifiedName())) {
        result.add(annotation);
      }
    }

    if (listOwner instanceof PsiMethod) {
      PsiAnnotation hardcoded = getHardcodedContractAnnotation((PsiMethod)listOwner);
      if (hardcoded != null) {
        result.add(hardcoded);
      } else if (!ignoreInference(listOwner, ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
        ContainerUtil.addIfNotNull(result, getInferredContractAnnotation((PsiMethod)listOwner));
      }

      if (!ignoreInference(listOwner, AnnotationUtil.NOT_NULL) || !ignoreInference(listOwner, AnnotationUtil.NULLABLE)) {
        PsiAnnotation annotation = getInferredNullityAnnotation((PsiMethod)listOwner);
        if (annotation != null && !ignoreInference(listOwner, annotation.getQualifiedName())) {
          result.add(annotation);
        }
      }
    }

    return result.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
