// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NonCodeAnnotationGenerator {

  @NotNull
  public static MultiMap<PsiModifierListOwner, AnnotationDocGenerator> getSignatureNonCodeAnnotations(PsiModifierListOwner owner) {
    MultiMap<PsiModifierListOwner, AnnotationDocGenerator> generators = MultiMap.createLinked();
    for (PsiModifierListOwner each : getSignatureOwners(owner)) {
      List<AnnotationDocGenerator> nonCode =
        ContainerUtil.filter(AnnotationDocGenerator.getAnnotationsToShow(each), a -> a.isExternal() || a.isInferred());
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
    if (owner instanceof PsiMethod) {
      Collections.addAll(allOwners, ((PsiMethod)owner).getParameterList().getParameters());
    }
    return allOwners;
  }

  @NotNull
  public static @Nls String getNonCodeHeaderAvalable(Collection<AnnotationDocGenerator> values) {
    boolean hasExternal = values.stream().anyMatch(AnnotationDocGenerator::isExternal);
    boolean hasInferred = values.stream().anyMatch(AnnotationDocGenerator::isInferred);

    if (hasExternal && hasInferred) {
      return JavaBundle.message("non.code.annotations.explanation.external.and.inferred.available");
    }
    if (hasExternal) {
      return JavaBundle.message("non.code.annotations.explanation.external.available");
    }
    return JavaBundle.message("non.code.annotations.explanation.inferred.available");
  }
}
