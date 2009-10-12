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

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;

/**
 * @author yole
 */
public class JavaGotoTargetRendererProvider implements GotoTargetRendererProvider {
  public PsiElementListCellRenderer getRenderer(final PsiElement[] elements) {
    boolean onlyMethods = true;
    boolean onlyClasses = true;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiMethod)) onlyMethods = false;
      if (!(element instanceof PsiClass)) onlyClasses = false;
    }
    if (onlyMethods) {
      return new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(Arrays.asList(elements).toArray(PsiMethod.EMPTY_ARRAY)));
    }
    else if (onlyClasses) {
      return new PsiClassListCellRenderer();
    }
    return null;
  }

}
