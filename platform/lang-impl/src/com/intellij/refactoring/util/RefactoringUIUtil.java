package com.intellij.refactoring.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.IncorrectOperationException;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;

/**
 * @author yole
 */
public class RefactoringUIUtil {
  private RefactoringUIUtil() {
  }

  public static String getDescription(@NotNull PsiElement element, boolean includeParent) {
    return ElementDescriptionUtil.getElementDescription(element, includeParent
                                                                 ? RefactoringDescriptionLocation.WITH_PARENT
                                                                 : RefactoringDescriptionLocation.WITHOUT_PARENT);
  }

  public static void processIncorrectOperation(final Project project, IncorrectOperationException e) {
    @NonNls String message = e.getMessage();
    final int index = message != null ? message.indexOf("java.io.IOException") : -1;
    if (index > 0) {
      message = message.substring(index + "java.io.IOException".length());
    }

    final String s = message;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showMessageDialog(project, s, RefactoringBundle.message("error.title"), Messages.getErrorIcon());
      }
    });
  }

  public static String calculatePsiElementDescriptionList(PsiElement[] elements) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < elements.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(UsageViewUtil.getType(elements[i]));
      buffer.append(" ");
      buffer.append(UsageViewUtil.getDescriptiveName(elements[i]));
    }

    return buffer.toString();
  }
}
