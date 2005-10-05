package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  public static void showErrorMessage(String title, String message, String helpId, Project project) {
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project);
    dialog.show();
  }

  public static String htmlEmphasize(String text) {
    @NonNls final String header = "<b><code>";
    @NonNls final String footer = "</code></b>";
    return new StringBuilder().append(header).append(text).append(footer).toString();
  }
}
