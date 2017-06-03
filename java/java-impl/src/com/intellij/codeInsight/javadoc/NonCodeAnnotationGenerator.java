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

class NonCodeAnnotationGenerator {
  private final List<PsiModifierListOwner> myAllOwners = new ArrayList<>();
  private final StringBuilder myOutput;

  NonCodeAnnotationGenerator(PsiModifierListOwner owner, StringBuilder output) {
    myOutput = output;
    myAllOwners.add(owner);
    if (owner instanceof PsiMethod) {
      Collections.addAll(myAllOwners, ((PsiMethod)owner).getParameterList().getParameters());
    }
  }

  void explainAnnotations() {
    MultiMap<PsiModifierListOwner, AnnotationDocGenerator> generators = getNonCodeAnnotations();
    if (generators.isEmpty()) return;
    
    boolean hasExternal = generators.values().stream().anyMatch(AnnotationDocGenerator::isExternal);
    boolean hasInferred = generators.values().stream().anyMatch(AnnotationDocGenerator::isInferred);
    boolean hasBothKinds = hasExternal && hasInferred;

    myOutput.append("\n");
    myOutput.append(
      hasBothKinds ? "External and <i>inferred</i>" : hasExternal ? "External" : "<i>Inferred</i>").append(" annotations available:<br>\n");
    myOutput.append("<ul>\n");

    generators.keySet().forEach(owner -> {
      myOutput.append("<li>");
      if (generators.size() > 1) {
        myOutput.append(getKind(owner)).append(" <code>").append(((PsiNamedElement)owner).getName()).append("</code>: ");
      }
      List<AnnotationDocGenerator> annotations = ContainerUtil.newArrayList(generators.get(owner));
      for (int i = 0; i < annotations.size(); i++) {
        if (i > 0) myOutput.append(" ");
        annotations.get(i).generateAnnotation(myOutput, AnnotationFormat.JavaDocComplete);
      }
      
      myOutput.append("</li>\n");
    });
    myOutput.append("</ul>\n");
  }

  @NotNull
  private MultiMap<PsiModifierListOwner, AnnotationDocGenerator> getNonCodeAnnotations() {
    MultiMap<PsiModifierListOwner, AnnotationDocGenerator> generators = MultiMap.createLinked();
    for (PsiModifierListOwner owner : myAllOwners) {
      List<AnnotationDocGenerator> nonCode =
        ContainerUtil.filter(AnnotationDocGenerator.getAnnotationsToShow(owner), a -> a.isExternal() || a.isInferred());
      if (!nonCode.isEmpty()) {
        generators.putValues(owner, nonCode);
      }
    }
    return generators;
  }

  private static String getKind(PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter) return "Parameter";
    if (owner instanceof PsiMethod) {
      return ((PsiMethod)owner).isConstructor() ? "Constructor" : "Method";
    }
    return owner.getClass().getName(); // unexpected
  }
}
