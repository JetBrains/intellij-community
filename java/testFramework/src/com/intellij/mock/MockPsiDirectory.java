// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockPsiDirectory extends MockPsiElement implements PsiDirectory {
  private final PsiPackage myPackage;
  private final Project myProject;

  public MockPsiDirectory(final PsiPackage aPackage, @NotNull Disposable parentDisposable) {
    super(parentDisposable);
    myPackage = aPackage;
    myProject = null;
  }

  public MockPsiDirectory(Project project, @NotNull Disposable parentDisposable) {
    super(parentDisposable);
    myProject = project;
    myPackage = null;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject != null ? myProject : super.getProject();
  }

  @Override
  public @NotNull Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public void checkCreateFile(final @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkCreateSubdirectory(final @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiDirectory getParent() {
    return getParentDirectory();
  }


  @Override
  public @NotNull PsiFile createFile(final @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull PsiFile copyFileFrom(final @NotNull String newName, final @NotNull PsiFile originalFile) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method copyFileFrom is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull PsiDirectory createSubdirectory(final @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiFile findFile(final @NotNull @NonNls String name) {
    throw new UnsupportedOperationException("Method findFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiDirectory findSubdirectory(final @NotNull String name) {
    throw new UnsupportedOperationException("Method findSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile @NotNull [] getFiles() {
    throw new UnsupportedOperationException("Method getFiles is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile @NotNull [] getFiles(@NotNull GlobalSearchScope scope) {
    throw new UnsupportedOperationException("Method getFiles is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull String getName() {
    throw new UnsupportedOperationException("Method getName is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiDirectory getParentDirectory() {
    final PsiPackage psiPackage = myPackage.getParentPackage();
    return psiPackage == null ? null : new MockPsiDirectory(psiPackage, getProject());
  }

  @Override
  public PsiDirectory @NotNull [] getSubdirectories() {
    throw new UnsupportedOperationException("Method getSubdirectories is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return new LightVirtualFile();
  }

  @Override
  public boolean processChildren(final @NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
    throw new UnsupportedOperationException("Method processChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull PsiElement setName(final @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method setName is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkSetName is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable ItemPresentation getPresentation() {
    throw new UnsupportedOperationException("Method getPresentation is not yet implemented in " + getClass().getName());
  }
}
