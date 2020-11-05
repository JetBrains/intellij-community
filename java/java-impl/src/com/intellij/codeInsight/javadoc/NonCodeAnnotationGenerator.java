// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.java.JavaBundle;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NonCodeAnnotationGenerator {
  private final PsiModifierListOwner myOwner;
  private final StringBuilder myOutput;

  NonCodeAnnotationGenerator(@NotNull PsiModifierListOwner owner, StringBuilder output) {
    myOwner = owner;
    myOutput = output;
  }

  void explainAnnotations() {
    MultiMap<PsiModifierListOwner, AnnotationDocGenerator> generators = getSignatureNonCodeAnnotations(myOwner);
    if (generators.isEmpty()) return;

    myOutput.append(DocumentationMarkup.SECTION_HEADER_START);
    myOutput.append(getNonCodeHeader(generators.values())).append(":");
    myOutput.append(DocumentationMarkup.SECTION_SEPARATOR);

    generators.keySet().forEach(owner -> {
      myOutput.append("<p>");
      if (generators.size() > 1) {
        myOutput.append(getKind(owner)).append(" <code>").append(((PsiNamedElement)owner).getName()).append("</code>: ");
      }
      List<AnnotationDocGenerator> annotations = new ArrayList<>(generators.get(owner));
      for (int i = 0; i < annotations.size(); i++) {
        if (i > 0) myOutput.append(" ");
        annotations.get(i).generateAnnotation(myOutput, AnnotationFormat.JavaDocComplete);
      }
    });
    myOutput.append(DocumentationMarkup.SECTION_END);
  }

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
  public static @Nls String getNonCodeHeader(Collection<AnnotationDocGenerator> values) {
    boolean hasExternal = values.stream().anyMatch(AnnotationDocGenerator::isExternal);
    boolean hasInferred = values.stream().anyMatch(AnnotationDocGenerator::isInferred);

    if (hasExternal && hasInferred) {
      return JavaBundle.message("non.code.annotations.explanation.external.and.inferred");
    }
    if (hasExternal) {
      return JavaBundle.message("non.code.annotations.explanation.external");
    }
    return JavaBundle.message("non.code.annotations.explanation.inferred");
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

  private static String getKind(PsiModifierListOwner owner) {
    return StringUtil.capitalize(JavaElementKind.fromElement(owner).subject());
  }
}
