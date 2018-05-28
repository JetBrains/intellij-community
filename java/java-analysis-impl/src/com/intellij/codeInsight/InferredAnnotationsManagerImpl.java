// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.*;
import static com.intellij.codeInspection.dataFlow.JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;

public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  private static final Set<String> INFERRED_ANNOTATIONS =
    ContainerUtil.set(NOT_NULL, NULLABLE, ORG_JETBRAINS_ANNOTATIONS_CONTRACT, Mutability.UNMODIFIABLE_ANNOTATION,
                      Mutability.UNMODIFIABLE_VIEW_ANNOTATION);
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

    if ((NOT_NULL.equals(annotationFQN) || NULLABLE.equals(annotationFQN))) {
      PsiAnnotation anno = null;
      if (listOwner instanceof PsiMethodImpl) {
        anno = getInferredNullityAnnotation((PsiMethodImpl)listOwner);
      }
      if (listOwner instanceof PsiParameter) {
        anno = getInferredNullityAnnotation((PsiParameter)listOwner);
      }
      return anno == null ? null : annotationFQN.equals(anno.getQualifiedName()) ? anno : null;
    }

    if (Mutability.UNMODIFIABLE_ANNOTATION.equals(annotationFQN) || Mutability.UNMODIFIABLE_VIEW_ANNOTATION.equals(annotationFQN)) {
      return getInferredMutabilityAnnotation(listOwner);
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
    if (NOT_NULL.equals(annotationFQN) && owner instanceof PsiParameter && owner.getParent() != null) {
      List<String> annotations = NullableNotNullManager.getInstance(owner.getProject()).getNullables();
      if (isAnnotated(owner, annotations, CHECK_EXTERNAL | CHECK_INFERRED | CHECK_TYPE)) {
        return true;
      }
      if (HardcodedContracts.hasHardcodedContracts(owner)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private PsiAnnotation getInferredMutabilityAnnotation(@NotNull PsiModifierListOwner owner) {
    if (!(owner instanceof PsiMethodImpl)) return null;
    PsiMethodImpl method = (PsiMethodImpl)owner;
    PsiModifierList modifiers = (method).getModifierList();
    if (modifiers.hasAnnotation(Mutability.UNMODIFIABLE_ANNOTATION) ||
        modifiers.hasAnnotation(Mutability.UNMODIFIABLE_VIEW_ANNOTATION)) {
      return null;
    }
    return JavaSourceInference.inferMutability(method).asAnnotation(myProject);
  }

  @Nullable
  private PsiAnnotation getInferredContractAnnotation(PsiMethodImpl method) {
    if (method.getModifierList().hasAnnotation(ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
      return null;
    }

    return createContractAnnotation(JavaSourceInference.inferContracts(method), JavaSourceInference.inferPurity(method));
  }

  @Nullable
  private PsiAnnotation getInferredNullityAnnotation(PsiMethodImpl method) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    if (findAnnotation(method, manager.getNotNulls(), true) != null || findAnnotation(method, manager.getNullables(), true) != null) {
      return null;
    }

    if (manager.findNullityDefaultInHierarchy(method) != null) {
      return null;
    }

    Nullness nullness = JavaSourceInference.inferNullity(method);
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
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    if (findAnnotation(parameter, manager.getNotNulls(), true) != null || findAnnotation(parameter, manager.getNullables(), true) != null) {
      return null;
    }
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
    Nullness nullness = JavaSourceInference.inferNullity(parameter);
    return nullness == Nullness.NOT_NULL ? ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation() : null;
  }

  @Nullable
  private PsiAnnotation createContractAnnotation(List<? extends MethodContract> contracts, boolean pure) {
    return createContractAnnotation(myProject, pure, StreamEx.of(contracts).select(StandardMethodContract.class).joining("; "), "");
  }

  @Nullable
  public static PsiAnnotation createContractAnnotation(Project project, boolean pure, String contracts, String mutates) {
    Map<String, String> attrMap = new LinkedHashMap<>();
    if (!contracts.isEmpty()) {
      attrMap.put("value", StringUtil.wrapWithDoubleQuote(contracts));
    }
    if (pure) {
      attrMap.put("pure", "true");
    }
    else if (!mutates.trim().isEmpty()) {
      attrMap.put("mutates", StringUtil.wrapWithDoubleQuote(mutates));
    }
    if (attrMap.isEmpty()) {
      return null;
    }
    String attrs = attrMap.keySet().equals(Collections.singleton("value")) ?
                   attrMap.get("value") : EntryStream.of(attrMap).join(" = ").joining(", ");
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

        if (!ignoreInference(listOwner, NOT_NULL) || !ignoreInference(listOwner, NULLABLE)) {
          PsiAnnotation annotation = getInferredNullityAnnotation((PsiMethodImpl)listOwner);
          if (annotation != null && !ignoreInference(listOwner, annotation.getQualifiedName())) {
            result.add(annotation);
          }
        }
      }
    }

    if (listOwner instanceof PsiParameter && !ignoreInference(listOwner, NOT_NULL)) {
      ContainerUtil.addIfNotNull(result, getInferredNullityAnnotation((PsiParameter)listOwner));
    }

    ContainerUtil.addIfNotNull(result, getInferredMutabilityAnnotation(listOwner));

    return result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(ProjectBytecodeAnalysis.INFERRED_ANNOTATION) != null;
  }
}
