package com.intellij.codeInspection;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MoveToPackageFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.MoveToPackageFix");
  private PsiFile myFile;
  private PsiPackage myTargetPackage;

  public MoveToPackageFix(PsiFile file, PsiPackage targetPackage) {
    myFile = file;
    myTargetPackage = targetPackage;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("move.class.to.package.text", myTargetPackage.getQualifiedName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.package.family");
  }

  public boolean isAvailable() {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myFile instanceof PsiJavaFile
        && ((PsiJavaFile) myFile).getClasses().length != 0
        && myTargetPackage != null
        && myTargetPackage.isValid()
        ;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

    try {
      String packageName = myTargetPackage.getQualifiedName();
      PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(project, packageName, null, true);

      if (directory == null) {
        return;
      }
      String error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
      if (error != null) {
        Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
      new MoveClassesOrPackagesProcessor(
              project,
              new PsiElement[]{((PsiJavaFile) myFile).getClasses()[0]},
              new SingleSourceRootMoveDestination(PackageWrapper.create(directory.getPackage()), directory), false,
              false,
              null).run();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }


}
