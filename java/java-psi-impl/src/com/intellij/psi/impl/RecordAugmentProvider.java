// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
      String name = component.getName();
      if (name == null) continue;
      LightMethod method = new LightRecordMethod(element.getManager(), factory.createMethod(name, component.getType()), aClass, component);
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
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) return null;
    PsiClass aClass = factory.createClassFromText("private final " + typeElement.getText() + " " + name + ";", null);
    return aClass.getFields()[0];
  }
}
