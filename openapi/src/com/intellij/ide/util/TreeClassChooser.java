package com.intellij.ide.util;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;

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

  static interface ClassFilter {
    boolean isAccepted(PsiClass aClass);
  }

  static interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }

  static interface InheritanceClassFilter extends ClassFilter{
  }

}
