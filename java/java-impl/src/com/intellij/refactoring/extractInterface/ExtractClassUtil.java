package com.intellij.refactoring.extractInterface;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.refactoring.ui.YesNoPreviewUsagesDialog;

/**
 * @author dsl
 */
public class ExtractClassUtil {
  public static void askAndTurnRefsToSuper(final Project project, PsiClass aClass, final PsiClass aSuperClass) {
    final SmartPsiElementPointer classPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(aClass);
    final SmartPsiElementPointer interfacePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(aSuperClass);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final PsiElement classElement = classPointer.getElement();
        final PsiElement interfaceElement = interfacePointer.getElement();
        if (classElement instanceof PsiClass && interfaceElement instanceof PsiClass) {
          final PsiClass superClass = (PsiClass) interfaceElement;
          String superClassName = superClass.getName();
          String className = ((PsiClass) classElement).getName();
          String createdString = superClass.isInterface() ?
                                 RefactoringBundle.message("interface.has.been.successfully.created", superClassName) :
                                 RefactoringBundle.message("class.has.been.successfully.created", superClassName);
          String message = createdString + "\n" +
                           RefactoringBundle.message("use.super.references.prompt",
                             ApplicationNamesInfo.getInstance().getProductName(), className, superClassName);
          YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
            RefactoringBundle.message("analyze.and.replace.usages"),
            message,
            JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
            /*HelpID.TURN_REFS_TO_SUPER*/null, project);
          dialog.show();
          if (dialog.isOK()) {
            final boolean isPreviewUsages = dialog.isPreviewUsages();
            JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
            TurnRefsToSuperProcessor processor =
                    new TurnRefsToSuperProcessor(project, (PsiClass) classElement, (PsiClass) interfaceElement, true);
            processor.setPreviewUsages(isPreviewUsages);
            processor.run();
          }
        }
      }
    });
  }
}
