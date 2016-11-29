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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class GutterIconTooltipHelper {
  private GutterIconTooltipHelper() {
  }

  public static String composeText(@NotNull PsiElement[] elements, @NotNull String start, @NotNull String pattern) {
    return composeText(Arrays.asList(elements), start, pattern);
  }

  public static String composeText(@NotNull Iterable<? extends PsiElement> elements, @NotNull String start, @NotNull String pattern) {
    return composeText(elements, start, pattern, "");
  }

  public static String composeText(@NotNull Iterable<? extends PsiElement> elements, @NotNull String start, @NotNull String pattern, @NotNull String postfix) {
    @NonNls StringBuilder result = new StringBuilder();
    result.append("<html><body>");
    result.append(start);
    Set<String> names = new LinkedHashSet<>();
    for (PsiElement element : elements) {
      String descr = "";
      if (element instanceof PsiClass) {
        String className = ClassPresentationUtil.getNameForClass((PsiClass)element, true);
        descr = MessageFormat.format(pattern, className);
      }
      else if (element instanceof PsiMethod) {
        String methodName = ((PsiMethod)element).getName();
        PsiClass aClass = ((PsiMethod)element).getContainingClass();
        String className = aClass == null ? "" : ClassPresentationUtil.getNameForClass(aClass, true);
        descr = MessageFormat.format(pattern, methodName, className);
      }
      else if (element instanceof PsiFile) {
        descr = MessageFormat.format(pattern, ((PsiFile)element).getName());
      }
      names.add(descr);
    }

    @NonNls String sep = "";
    for (String name : names) {
      result.append(sep);
      sep = "<br>";
      result.append(name);
    }
    result.append(postfix);
    result.append("</body></html>");
    return result.toString();
  }
}
