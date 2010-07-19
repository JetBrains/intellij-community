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
package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.Nullable;

public class PsiClassListCellRenderer extends PsiElementListCellRenderer<PsiClass> {
  public String getElementText(PsiClass element) {
    return ClassPresentationUtil.getNameForClass(element, false);
  }

  protected String getContainerText(PsiClass element, final String name) {
    return getContainerTextStatic(element);
  }

  @Nullable
  protected static String getContainerTextStatic(final PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiClassOwner) {
      String packageName = ((PsiClassOwner)file).getPackageName();
      if (packageName.length() == 0) return null;
      return "(" + packageName + ")";
    }
    return null;
  }

  protected int getIconFlags() {
    return 0;
  }
}
