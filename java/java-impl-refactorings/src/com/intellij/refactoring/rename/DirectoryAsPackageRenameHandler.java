// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import org.jetbrains.annotations.NotNull;


public class DirectoryAsPackageRenameHandler extends DirectoryAsPackageRenameHandlerBase<PsiPackage> {

  @Override
  protected VirtualFile[] occursInPackagePrefixes(PsiPackage aPackage) {
    return aPackage.occursInPackagePrefixes();
  }

  @Override
  protected boolean isIdentifier(String name, Project project) {
    return PsiNameHelper.getInstance(project).isIdentifier(name);
  }

  @Override
  protected String getQualifiedName(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  @Override
  protected PsiPackage getPackage(PsiDirectory psiDirectory) {
    return JavaDirectoryService.getInstance().getPackageInSources(psiDirectory);
  }

  @Override
  protected BaseRefactoringProcessor createProcessor(final String newQName,
                                                     Project project,
                                                     final PsiDirectory[] dirsToRename,
                                                     boolean searchInComments, boolean searchInNonJavaFiles) {
    return new MoveDirectoryWithClassesProcessor(project, dirsToRename, null, searchInComments, searchInNonJavaFiles, false, null) {
      @Override
      public @NotNull TargetDirectoryWrapper getTargetDirectory(final PsiDirectory dir) {
        return new TargetDirectoryWrapper(dir.getParentDirectory(), StringUtil.getShortName(newQName));
      }

      @Override
      protected String getTargetName() {
        return newQName;
      }

      @Override
      protected @NotNull String getCommandName() {
        return RefactoringBundle.message(dirsToRename.length == 1 ? "rename.directory.command.name" : "rename.directories.command.name");
      }
    };
  }
}
