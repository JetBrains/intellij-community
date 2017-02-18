/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public class JavaMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {
  @Override
  @Nullable
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof PsiJavaFile) {
      ArrayList<PsiElement> array = new ArrayList<>();
      addNavigationElements(array, file);
      return MethodUpDownUtil.offsetsFromElements(array);      
    }
    return null;
  }

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiElement element) {
    PsiElement[] children = element.getChildren();
    boolean stopOnFields = Registry.is("ide.structural.navigation.visit.fields");
    for (PsiElement child : children) {
      if (child instanceof PsiMethod || child instanceof PsiClass || stopOnFields && child instanceof PsiField) {
        array.add(child);
        addNavigationElements(array, child);
      }
      if (element instanceof PsiClass && child instanceof PsiJavaToken && child.getText().equals("}")) {
        array.add(child);
      }
    }
  }
}
