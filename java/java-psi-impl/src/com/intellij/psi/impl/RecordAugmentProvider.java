// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class RecordAugmentProvider extends PsiAugmentProvider {
  @NotNull
  @Override
  protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      if (!aClass.isRecord()) return Collections.emptyList();
      if (type == PsiMethod.class) {
        return getAccessorsAugments(element, aClass);
      }
      if (type == PsiField.class) {
        return getFieldAugments(element, aClass);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getAccessorsAugments(@NotNull PsiElement element, PsiClass aClass) {
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    ArrayList<Psi> methods = new ArrayList<>(components.length);
    for (PsiRecordComponent component : components) {
      PsiMethod recordMethod = createRecordMethod(component, factory);
      if (recordMethod == null) continue;
      LightMethod method = new LightRecordMethod(element.getManager(), recordMethod, aClass, component);
      //noinspection unchecked
      methods.add((Psi)method);
    }
    return methods;
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getFieldAugments(@NotNull PsiElement element, PsiClass aClass) {
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    ArrayList<Psi> fields = new ArrayList<>(components.length);
    for (PsiRecordComponent component : components) {
      PsiField recordField = createRecordField(component, factory);
      if (recordField == null) continue;
      LightRecordField field = new LightRecordField(element.getManager(), recordField, aClass, component);
      //noinspection unchecked
      fields.add((Psi)field);
    }
    return fields;
  }

  @Nullable
  private static PsiField createRecordField(@NotNull PsiRecordComponent component, @NotNull PsiElementFactory factory) {
    String name = component.getName();
    if (name == null) return null;
    String typeText = getTypeText(component, RecordAugmentProvider::hasTargetApplicableForField);
    if (typeText == null) return null;
    PsiClass aClass = factory.createClassFromText("private final " + typeText + " " + name + ";", null);
    PsiField[] fields = aClass.getFields();
    if (fields.length != 1) return null;
    return fields[0];
  }

  @Nullable
  private static PsiMethod createRecordMethod(@NotNull PsiRecordComponent component, @NotNull PsiElementFactory factory) {
    String name = component.getName();
    if (name == null) return null;
    String typeText = getTypeText(component, RecordAugmentProvider::hasTargetApplicableForMethod);
    if (typeText == null) return null;
    PsiClass aClass = factory.createClassFromText("public " + typeText + " " + name + "(){}", null);
    PsiMethod[] methods = aClass.getMethods();
    if (methods.length != 1) return null;
    return methods[0];
  }

  @Nullable
  private static String getTypeText(@NotNull PsiRecordComponent component, Predicate<PsiAnnotation> annotationPredicate) {
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) return null;
    String annotations = Arrays.stream(component.getAnnotations())
        .filter(annotationPredicate)
        .map(annotation -> annotation.getText())
        .collect(Collectors.joining(" "));
    return annotations + " " + typeElement.getText();
  }

  private static boolean hasTargetApplicableForField(PsiAnnotation annotation) {
    Set<PsiAnnotation.TargetType> targets = getTargets(annotation);
    if (targets == null) return false;
    return targets.contains(PsiAnnotation.TargetType.TYPE) || targets.contains(PsiAnnotation.TargetType.FIELD);
  }

  private static boolean hasTargetApplicableForMethod(PsiAnnotation annotation) {
    Set<PsiAnnotation.TargetType> targets = getTargets(annotation);
    if (targets == null) return false;
    return targets.contains(PsiAnnotation.TargetType.TYPE) || targets.contains(PsiAnnotation.TargetType.METHOD);
  }

  private static Set<PsiAnnotation.TargetType> getTargets(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
    if (element == null) return null;
    PsiClass annotationClass = tryCast(element.resolve(), PsiClass.class);
    if (annotationClass == null) return null;
    return AnnotationTargetUtil.getAnnotationTargets(annotationClass);
  }
}
