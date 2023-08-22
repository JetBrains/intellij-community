// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public final class NonCodeAnnotationGenerator {

  @NotNull
  public static MultiMap<PsiModifierListOwner, AnnotationDocGenerator> getSignatureNonCodeAnnotations(PsiModifierListOwner owner) {
    MultiMap<PsiModifierListOwner, AnnotationDocGenerator> generators = MultiMap.createLinked();
    final Project project = owner.getProject();
    ExternalAnnotationsManager externalManager = ExternalAnnotationsManager.getInstance(project);
    InferredAnnotationsManager inferredManager = InferredAnnotationsManager.getInstance(project);
    for (PsiModifierListOwner each : getSignatureOwners(owner)) {
      Set<String> shownAnnotations = new HashSet<>();
      List<AnnotationDocGenerator> nonCode = StreamEx.of(externalManager.findExternalAnnotations(each),
                                                         inferredManager.findInferredAnnotations(each))
        .flatArray(Function.identity())
        .map(annotation -> AnnotationDocGenerator.forAnnotation(each, shownAnnotations, annotation))
        .nonNull()
        .toList();
      if (!nonCode.isEmpty()) {
        generators.putValues(each, nonCode);
      }
    }
    return generators;
  }

  @NotNull
  private static List<PsiModifierListOwner> getSignatureOwners(PsiModifierListOwner owner) {
    List<PsiModifierListOwner> allOwners = new ArrayList<>();
    allOwners.add(owner);
    if (owner instanceof PsiMethod method) {
      Collections.addAll(allOwners, method.getParameterList().getParameters());
    }
    return allOwners;
  }

  @NotNull
  public static @Nls String getNonCodeHeaderAvailable(Collection<AnnotationDocGenerator> values) {
    boolean hasExternal = ContainerUtil.exists(values, AnnotationDocGenerator::isExternal);
    boolean hasInferred = ContainerUtil.exists(values, AnnotationDocGenerator::isInferred);

    if (hasExternal && hasInferred) {
      return JavaBundle.message("non.code.annotations.explanation.external.and.inferred.available");
    }
    if (hasExternal) {
      return JavaBundle.message("non.code.annotations.explanation.external.available");
    }
    return JavaBundle.message("non.code.annotations.explanation.inferred.available");
  }
}
