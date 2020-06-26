// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

/**
 * @deprecated use {@link GutterTooltipHelper}
 */
@Deprecated
public final class GutterIconTooltipHelper {
  private GutterIconTooltipHelper() {
  }

  /**
   * @deprecated use {@link GutterTooltipHelper}
   */
  @Deprecated
  public static String composeText(PsiElement @NotNull [] elements, @NotNull String start, @NotNull String pattern) {
    return composeText(Arrays.asList(elements), start, pattern);
  }

  /**
   * @deprecated use {@link GutterTooltipHelper}
   */
  @Deprecated
  public static String composeText(@NotNull Iterable<? extends PsiElement> elements, @NotNull String start, @NotNull String pattern) {
    return composeText(elements, start, pattern, "");
  }

  /**
   * @deprecated use {@link GutterTooltipHelper}
   */
  @Deprecated
  static String composeText(@NotNull Iterable<? extends PsiElement> elements,
                            @NotNull String start,
                            @NotNull String pattern,
                            @NotNull String postfix) {
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
