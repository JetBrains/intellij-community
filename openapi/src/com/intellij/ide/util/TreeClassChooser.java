package com.intellij.ide.util;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;

/**
 * User: anna
 * Date: Jan 24, 2005
 */
public interface TreeClassChooser{

  public abstract PsiClass getSelectedClass();

  public abstract void selectClass(final PsiClass aClass);

  public abstract void selectDirectory(final PsiDirectory directory);

  public abstract void showDialog();

  public static interface ClassFilter {
    boolean isAccepted(PsiClass aClass);
  }

  public static interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }

  public static interface InheritanceClassFilter extends ClassFilter{
  }

}
