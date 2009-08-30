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

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Set;

public class GutterIconTooltipHelper {
  private GutterIconTooltipHelper() {
  }

  public static String composeText(PsiElement[] elements, String start, final String pattern) {
    @NonNls StringBuilder result = new StringBuilder();
    result.append("<html><body>");
    result.append(start);
    Set<String> names = new LinkedHashSet<String>();
    for (PsiElement element : elements) {
      String descr = "";
      if (element instanceof PsiClass) {
        String className = ClassPresentationUtil.getNameForClass((PsiClass)element, true);
        descr = MessageFormat.format(pattern, className);
      }
      else if (element instanceof PsiMethod) {
        String methodName = ((PsiMethod)element).getName();
        String className = ClassPresentationUtil.getNameForClass(((PsiMethod)element).getContainingClass(), true);
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

    result.append("</body></html>");
    return result.toString();
  }
}