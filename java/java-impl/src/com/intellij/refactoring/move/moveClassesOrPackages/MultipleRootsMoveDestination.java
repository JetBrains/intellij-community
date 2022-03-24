// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 *  @author dsl
 */
public class MultipleRootsMoveDestination extends AutocreatingMoveDestination {
  private static final Logger LOG = Logger.getInstance(MultipleRootsMoveDestination.class);

  public MultipleRootsMoveDestination(PackageWrapper aPackage) {
    super(aPackage);
  }

  @NotNull
  @Override
  public PackageWrapper getTargetPackage() {
    return myPackage;
  }


  @Override
  public PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException {
    //if (JavaDirectoryService.getInstance().isSourceRoot(source)) return null;
    return getOrCreateDirectoryForSource(source.getVirtualFile());
  }

  @Override
  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getOrCreateDirectoryForSource(source.getVirtualFile());
  }

  @Override
  public PsiDirectory getTargetIfExists(@NotNull PsiFile source) {
    return findTargetDirectoryForSource(source.getVirtualFile());
  }

  @Override
  public String verify(PsiFile source) {
    VirtualFile virtualFile = source.getVirtualFile();
    if (virtualFile.isDirectory()) {
      virtualFile = virtualFile.getParent();
      LOG.assertTrue(virtualFile.isDirectory());
    }

    final VirtualFile sourceRootForFile = myFileIndex.getSourceRootForFile(virtualFile);
    if (sourceRootForFile == null) {
      return "";
    }
    return checkCanCreateInSourceRoot(sourceRootForFile);
  }

  @Override
  @Nullable
  public String verify(PsiDirectory source) {
    VirtualFile virtualFile = source.getVirtualFile();
    final VirtualFile sourceRootForFile = myFileIndex.getSourceRootForFile(virtualFile);
    if (sourceRootForFile == null) {
      return "";
    }
    if (virtualFile.equals(sourceRootForFile)) return null;
    return checkCanCreateInSourceRoot(sourceRootForFile);
  }

  @Override
  @Nullable
  public String verify(PsiPackage source) {
    PsiDirectory[] directories = source.getDirectories();
    for (final PsiDirectory directory : directories) {
      String s = verify(directory);
      if (s != null) return s;
    }
    return null;
  }

  @Override
  public void analyzeModuleConflicts(@NotNull final Collection<? extends PsiElement> elements,
                                     @NotNull MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
  }

  @Override
  public boolean isTargetAccessible(@NotNull Project project, @NotNull VirtualFile place) {
    return true;
  }

  @Override
  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return findTargetDirectoryForSource(source.getVirtualFile());
  }


  private PsiDirectory findTargetDirectoryForSource(final VirtualFile file) {
    final VirtualFile sourceRoot = myFileIndex.getSourceRootForFile(file);
    LOG.assertTrue(sourceRoot != null);
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, sourceRoot);
  }

  private PsiDirectory getOrCreateDirectoryForSource(final VirtualFile file)
    throws IncorrectOperationException {
    return CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, Objects.requireNonNull(myFileIndex.getSourceRootForFile(file)));
  }
}
