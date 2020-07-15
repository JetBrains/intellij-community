
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NonNls;

public final class RefactoringMessageUtil {

  public static String getIncorrectIdentifierMessage(String identifierName) {
    return JavaRefactoringBundle.message("0.is.not.a.legal.java.identifier", identifierName);
  }

  /**
   * @return null, if can create a class
   *         an error message, if cannot create a class
   *
   */
  public static String checkCanCreateClass(PsiDirectory destinationDirectory, String className) {
    PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(destinationDirectory);
    VirtualFile file = destinationDirectory.getVirtualFile();
    for (PsiClass aClass : classes) {
      if (className.equals(aClass.getName())) {
        return JavaRefactoringBundle.message("directory.0.already.contains.1.named.2",
                                         file.getPresentableUrl(), UsageViewUtil.getType(aClass), className);
      }
    }
    @NonNls String fileName = className+".java";
    return checkCanCreateFile(destinationDirectory, fileName);
  }
  public static String checkCanCreateFile(PsiDirectory destinationDirectory, String fileName) {
    VirtualFile file = destinationDirectory.getVirtualFile();
    VirtualFile child = file.findChild(fileName);
    if (child != null) {
      return JavaRefactoringBundle.message("directory.0.already.contains.a.file.named.1",
                                       file.getPresentableUrl(), fileName);
    }
    return null;
  }

  public static String getGetterSetterMessage(String newName, String action, PsiMethod getter, PsiMethod setter) {
    String text;
    if (getter != null && setter != null) {
      text = JavaRefactoringBundle.message("getter.and.setter.methods.found.for.the.field.0", newName, action);
    }
    else if (getter != null) {
      text = JavaRefactoringBundle.message("getter.method.found.for.the.field.0", newName, action);
    }
    else {
      text = JavaRefactoringBundle.message("setter.method.found.for.the.field.0", newName, action);
    }
    return text;
  }

  public static void showNotSupportedForJspClassesError(final Project project, Editor editor, final String refactoringName, final String helpId) {
    String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId);
  }
}