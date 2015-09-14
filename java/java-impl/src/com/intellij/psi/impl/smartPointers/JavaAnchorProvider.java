/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class JavaAnchorProvider extends SmartPointerAnchorProvider {
  @Override
  public PsiElement getAnchor(@NotNull PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE) || !element.isPhysical()) {
      return null;
    }

    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass)element).getBaseClassReference().getReferenceNameElement();
      } else {
        return ((PsiClass)element).getNameIdentifier();
      }
    } else if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getNameIdentifier();
    } else if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getNameIdentifier();
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement restoreElement(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiIdentifier) {
      PsiElement parent = anchor.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
        parent = parent.getParent();
      }

      return parent;
    }
    return null;
  }
}
