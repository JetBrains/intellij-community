// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordAugmentProvider extends PsiAugmentProvider {
  @NotNull
  @Override
  protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      if (aClass.isRecord() && type == PsiMethod.class) {
        return getRecordAugments(element, aClass);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getRecordAugments(@NotNull PsiElement element, PsiClass aClass) {
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    ArrayList<Psi> methods = new ArrayList<>(components.length);
    for (PsiRecordComponent component : components) {
      String name = component.getName();
      if (name == null) continue;
      LightMethod method = new LightMethod(element.getManager(), factory.createMethod(name, component.getType()), aClass) {
        @Override
        public int getTextOffset() {
          return aClass.getTextOffset();
        }
      };
      //noinspection unchecked
      methods.add((Psi)method);
    }
    return methods;
  }
}
