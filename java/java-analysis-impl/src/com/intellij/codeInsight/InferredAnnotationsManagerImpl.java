/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;

public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  private static final Set<String> INFERRED_ANNOTATIONS =
    ContainerUtil.set(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
  private final Project myProject;

  public InferredAnnotationsManagerImpl(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!INFERRED_ANNOTATIONS.contains(annotationFQN)) {
      return null;
    }

    listOwner = PsiUtil.preferCompiledElement(listOwner);

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

    if ((AnnotationUtil.NOT_NULL.equals(annotationFQN) || AnnotationUtil.NULLABLE.equals(annotationFQN))) {
      PsiAnnotation anno = null;
      if (listOwner instanceof PsiMethodImpl) {
        anno = getInferredNullityAnnotation((PsiMethodImpl)listOwner);
      }
      if (listOwner instanceof PsiParameter) {
        anno = getInferredNullityAnnotation((PsiParameter)listOwner);
      }
      return anno == null ? null : annotationFQN.equals(anno.getQualifiedName()) ? anno : null;
    }

    if (listOwner instanceof PsiMethodImpl && ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN)) {
      return getInferredContractAnnotation((PsiMethodImpl)listOwner);
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
    if (owner instanceof PsiMethod && PsiUtil.canBeOverridden((PsiMethod)owner)) {
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
  private PsiAnnotation getInferredContractAnnotation(PsiMethodImpl method) {
    if (method.getModifierList().findAnnotation(ORG_JETBRAINS_ANNOTATIONS_CONTRACT) != null) {
      return null;
    }

    return createContractAnnotation(ContractInference.inferContracts(method), PurityInference.inferPurity(method));
  }

  @Nullable
  private PsiAnnotation getInferredNullityAnnotation(PsiMethodImpl method) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    if (AnnotationUtil.findAnnotation(method, manager.getNotNulls(), true) != null || AnnotationUtil.findAnnotation(method, manager.getNullables(), true) != null) {
      return null;
    }

    if (NullableNotNullManager.findNullabilityDefaultInHierarchy(method, true) != null ||
        NullableNotNullManager.findNullabilityDefaultInHierarchy(method, false) != null) {
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
  private PsiAnnotation getInferredNullityAnnotation(PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (!(parent instanceof PsiParameterList)) return null;
    PsiElement scope = parent.getParent();
    if (scope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)scope;
      if (method.getName().equals("of")) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          String className = containingClass.getQualifiedName();
          if (CommonClassNames.JAVA_UTIL_LIST.equals(className) ||
              CommonClassNames.JAVA_UTIL_SET.equals(className) ||
              CommonClassNames.JAVA_UTIL_MAP.equals(className) ||
              "java.util.EnumSet".equals(className)) {
            return ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation();
          }
        }
      }
    }
    Nullness nullness = NullityInference.inferNullity(parameter);
    return nullness == Nullness.NOT_NULL ? ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation() : null;
  }

  @Nullable
  private PsiAnnotation createContractAnnotation(List<? extends MethodContract> contracts, boolean pure) {
    return createContractAnnotation(myProject, pure, StreamEx.of(contracts).select(StandardMethodContract.class).joining("; "));
  }

  @Nullable
  public static PsiAnnotation createContractAnnotation(Project project, boolean pure, String contracts) {
    final String attrs;
    if (!contracts.isEmpty() && pure) {
      attrs = "value = " + "\"" + contracts + "\", pure = true";
    } else if (pure) {
      attrs = "pure = true";
    } else if (!contracts.isEmpty()) {
      attrs = "\"" + contracts + "\"";
    } else {
      return null;
    }
    return ProjectBytecodeAnalysis.getInstance(project).createContractAnnotation(attrs);
  }

  @NotNull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    listOwner = PsiUtil.preferCompiledElement(listOwner);
    List<PsiAnnotation> result = ContainerUtil.newArrayList();
    PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotations(listOwner);
    for (PsiAnnotation annotation : fromBytecode) {
      if (!ignoreInference(listOwner, annotation.getQualifiedName())) {
        result.add(annotation);
      }
    }

    if (listOwner instanceof PsiMethod) {
      PsiAnnotation hardcoded = getHardcodedContractAnnotation((PsiMethod)listOwner);
      ContainerUtil.addIfNotNull(result, hardcoded);
      if (listOwner instanceof PsiMethodImpl) {
        if (hardcoded == null && !ignoreInference(listOwner, ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
          ContainerUtil.addIfNotNull(result, getInferredContractAnnotation((PsiMethodImpl)listOwner));
        }

        if (!ignoreInference(listOwner, AnnotationUtil.NOT_NULL) || !ignoreInference(listOwner, AnnotationUtil.NULLABLE)) {
          PsiAnnotation annotation = getInferredNullityAnnotation((PsiMethodImpl)listOwner);
          if (annotation != null && !ignoreInference(listOwner, annotation.getQualifiedName())) {
            result.add(annotation);
          }
        }
      }
    }

    if (listOwner instanceof PsiParameter && !ignoreInference(listOwner, AnnotationUtil.NOT_NULL)) {
      ContainerUtil.addIfNotNull(result, getInferredNullityAnnotation((PsiParameter)listOwner));
    }

    return result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
