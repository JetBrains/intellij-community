package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * User: anna
 * Date: Jan 24, 2005
 */
public interface TreeClassChooser{

  PsiClass getSelectedClass();

  void selectClass(final PsiClass aClass);

  void selectDirectory(final PsiDirectory directory);

  void showDialog();

  void showPopup();

  interface ClassFilter {
    boolean isAccepted(PsiClass aClass);
  }

  interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }

  interface InheritanceClassFilter extends ClassFilter{
  }

}
