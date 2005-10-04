package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  public static void showErrorMessage(String title, String message, String helpId, Project project) {
    RefactoringMessageDialog dialog=new RefactoringMessageDialog(title,message,helpId,"OptionPane.errorIcon",false, project);
    dialog.show();
  }
}
