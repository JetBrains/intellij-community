/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
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
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, sourceRoot);
  }

  private PsiDirectory getOrCreateDirectoryForSource(final VirtualFile file)
    throws IncorrectOperationException {
    return RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, Objects.requireNonNull(myFileIndex.getSourceRootForFile(file)));
  }
}
