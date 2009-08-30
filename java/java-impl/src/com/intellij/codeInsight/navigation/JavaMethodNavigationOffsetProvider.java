package com.intellij.codeInsight.navigation;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public class JavaMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {
  @Nullable
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof PsiJavaFile) {
      ArrayList<PsiElement> array = new ArrayList<PsiElement>();
      addNavigationElements(array, file);
      return MethodUpDownUtil.offsetsFromElements(array);      
    }
    return null;
  }

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiElement element) {
    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiMethod || child instanceof PsiClass) {
        array.add(child);
        addNavigationElements(array, child);
      }
      if (element instanceof PsiClass && child instanceof PsiJavaToken && child.getText().equals("}")) {
        array.add(child);
      }
    }
  }
}
