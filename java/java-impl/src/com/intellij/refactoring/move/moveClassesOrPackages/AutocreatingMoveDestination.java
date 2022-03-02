// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
public abstract class AutocreatingMoveDestination implements MoveDestination {
  @NotNull
  protected final PackageWrapper myPackage;
  protected final PsiManager myManager;
  protected final ProjectFileIndex myFileIndex;

  public AutocreatingMoveDestination(@NotNull PackageWrapper targetPackage) {
    myPackage = targetPackage;
    myManager = myPackage.getManager();
    myFileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
  }

  @NotNull
  @Override
  public abstract PackageWrapper getTargetPackage();

  @Override
  public abstract PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException;

  @Override
  public abstract PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException;

  @Nullable
  protected @NlsContexts.DialogMessage String checkCanCreateInSourceRoot(final VirtualFile targetSourceRoot) {
    final String targetQName = myPackage.getQualifiedName();
    final String sourceRootPackage = myFileIndex.getPackageNameByDirectory(targetSourceRoot);
    if (!CommonJavaRefactoringUtil.canCreateInSourceRoot(sourceRootPackage, targetQName)) {
      return JavaRefactoringBundle.message("source.folder.0.has.package.prefix.1", targetSourceRoot.getPresentableUrl(),
                                       sourceRootPackage, targetQName);
    }
    return null;
  }
}
