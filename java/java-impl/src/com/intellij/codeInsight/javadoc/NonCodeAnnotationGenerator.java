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
package com.intellij.codeInsight.javadoc;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
      List<AnnotationDocGenerator> annotations = ContainerUtil.newArrayList(generators.get(owner));
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
  public static String getNonCodeHeader(Collection<? extends AnnotationDocGenerator> values) {
    boolean hasExternal = values.stream().anyMatch(AnnotationDocGenerator::isExternal);
    boolean hasInferred = values.stream().anyMatch(AnnotationDocGenerator::isInferred);

    return (hasExternal && hasInferred ? "External and <i>inferred</i>" : hasExternal ? "External" : "<i>Inferred</i>") + " annotations";
  }

  private static String getKind(PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter) return "Parameter";
    if (owner instanceof PsiMethod) {
      return ((PsiMethod)owner).isConstructor() ? "Constructor" : "Method";
    }
    return owner.getClass().getName(); // unexpected
  }
}
