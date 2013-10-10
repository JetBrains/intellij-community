/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class JavaContainerProvider implements ContainerProvider {
  @Override
  public PsiElement getContainer(@NotNull PsiElement item) {
    if (item instanceof PsiTypeParameter) {
      PsiElement parent = item.getParent();
      return parent == null ? null : parent.getParent();
    }
    if (item instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)item).getContainingClass();
      return containingClass == null ? item.getContainingFile() : containingClass;
    }
    return null;
  }
}
