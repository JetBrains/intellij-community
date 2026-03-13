// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInsight.hints.AnnotationInlayProviderKt;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class NonCodeAnnotationGenerator {

  public static @NotNull MultiMap<PsiElement, AnnotationDocGenerator> getSignatureNonCodeAnnotations(PsiModifierListOwner owner) {
    MultiMap<PsiElement, AnnotationDocGenerator> generators = MultiMap.createLinked();
    final Project project = owner.getProject();
    ExternalAnnotationsManager externalManager = ExternalAnnotationsManager.getInstance(project);
    InferredAnnotationsManager inferredManager = InferredAnnotationsManager.getInstance(project);
    for (PsiModifierListOwner each : getSignatureOwners(owner)) {
      Set<String> shownAnnotations = new HashSet<>();
      List<AnnotationDocGenerator> nonCode = getGenerators(each, each, externalManager, inferredManager, shownAnnotations);
      if (!nonCode.isEmpty()) {
        generators.putValues(each, nonCode);
      }
      if (each instanceof PsiTypeParameterListOwner typeParameterListOwner) {
        PsiTypeParameter[] originalParameters =
          ((PsiTypeParameterListOwner)typeParameterListOwner.getOriginalElement()).getTypeParameters();
        PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
        if (originalParameters.length == parameters.length) {
          for (int i = 0; i < parameters.length; i++) {
            AnnotationInlayProviderKt.processTypeParameterAnnotationRecursively(
              parameters[i],
              originalParameters[i],
              new Function2<>() {
                @Override
                public Unit invoke(PsiJavaCodeReferenceElement element,
                                   PsiClassType type) {
                  List<AnnotationDocGenerator> typeAnnotationGenerators =
                    getGenerators(element, type.getAnnotations(),
                                  externalManager, inferredManager,
                                  new HashSet<>());
                  if (!typeAnnotationGenerators.isEmpty()) {
                    generators.putValues(element, typeAnnotationGenerators);
                  }
                  return Unit.INSTANCE;
                }
              },
              new Function2<>() {
                @Override
                public Unit invoke(PsiTypeElement originalTypeElement,
                                   PsiTypeElement typeElement) {
                  List<AnnotationDocGenerator> typeAnnotationGenerators =
                    getGenerators(typeElement, originalTypeElement.getType()
                                    .getAnnotations(),
                                  externalManager, inferredManager,
                                  new HashSet<>());
                  if (!typeAnnotationGenerators.isEmpty()) {
                    generators.putValues(typeElement,
                                         typeAnnotationGenerators);
                  }
                  return Unit.INSTANCE;
                }
              },
              new Function2<>() {
                @Override
                public Unit invoke(PsiTypeParameter originalParameter,
                                   PsiTypeParameter parameter) {
                  List<AnnotationDocGenerator> typeAnnotationGenerators =
                    getGenerators(originalParameter, parameter,
                                  externalManager, inferredManager,
                                  new HashSet<>());
                  if (!typeAnnotationGenerators.isEmpty()) {
                    generators.putValues(parameter,
                                         typeAnnotationGenerators);
                  }
                  return Unit.INSTANCE;
                }
              });
          }
        }
      }

      PsiElement element = each.getOriginalElement();
      PsiTypeElement typeElement = switch (element) {
        case PsiMethod method -> method.getReturnTypeElement();
        case PsiVariable variable -> variable.getTypeElement();
        default -> null;
      };
      if (typeElement != null) {
        typeElement.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
            List<AnnotationDocGenerator> typeAnnotationGenerators =
              getGenerators(typeElement, typeElement.getType().getAnnotations(), externalManager, inferredManager, new HashSet<>());
            if (!typeAnnotationGenerators.isEmpty()) {
              generators.putValues(typeElement, typeAnnotationGenerators);
            }
            super.visitTypeElement(typeElement);
          }
        });
      }
    }

    return generators;
  }

  private static @NotNull List<AnnotationDocGenerator> getGenerators(@NotNull PsiElement context,
                                                                     @NotNull PsiAnnotation @NotNull [] annotations,
                                                                     @NotNull ExternalAnnotationsManager externalManager,
                                                                     @NotNull InferredAnnotationsManager inferredManager,
                                                                     @NotNull Set<String> shownAnnotations) {

    List<AnnotationDocGenerator> nonCode = StreamEx.of(annotations)
      .filter(anno -> externalManager.isExternalAnnotation(anno) || inferredManager.isInferredAnnotation(anno))
      .map(annotation -> AnnotationDocGenerator.forAnnotation(context, shownAnnotations, annotation))
      .nonNull()
      .toList();
    return nonCode;
  }

  private static @NotNull List<AnnotationDocGenerator> getGenerators(@NotNull PsiModifierListOwner each,
                                                                     @NotNull PsiElement context,
                                                                     @NotNull ExternalAnnotationsManager externalManager,
                                                                     @NotNull InferredAnnotationsManager inferredManager,
                                                                     @NotNull Set<String> shownAnnotations) {
    List<AnnotationDocGenerator> nonCode = StreamEx.of(externalManager.findExternalAnnotations(each),
                                                       inferredManager.findInferredAnnotations(each))
      .flatArray(Function.identity())
      .map(annotation -> AnnotationDocGenerator.forAnnotation(context, shownAnnotations, annotation))
      .nonNull()
      .toList();
    return nonCode;
  }

  private static @NotNull List<PsiModifierListOwner> getSignatureOwners(PsiModifierListOwner owner) {
    List<PsiModifierListOwner> allOwners = new ArrayList<>();
    allOwners.add(owner);
    if (owner instanceof PsiMethod method) {
      Collections.addAll(allOwners, method.getParameterList().getParameters());
    }
    return allOwners;
  }

  public static @NotNull @Nls String getNonCodeHeaderAvailable(Collection<AnnotationDocGenerator> values) {
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
