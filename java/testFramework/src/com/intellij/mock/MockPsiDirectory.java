/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
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

  @NotNull
  @Override
  public Project getProject() {
    return myProject != null ? myProject : super.getProject();
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public void checkCreateFile(@NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkCreateSubdirectory(@NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiDirectory getParent() {
    return getParentDirectory();
  }


  @Override
  @NotNull
  public PsiFile createFile(@NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiFile copyFileFrom(@NotNull final String newName, @NotNull final PsiFile originalFile) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method copyFileFrom is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiDirectory createSubdirectory(@NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiFile findFile(@NotNull @NonNls final String name) {
    throw new UnsupportedOperationException("Method findFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiDirectory findSubdirectory(@NotNull final String name) {
    throw new UnsupportedOperationException("Method findSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiFile[] getFiles() {
    throw new UnsupportedOperationException("Method getFiles is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public String getName() {
    throw new UnsupportedOperationException("Method getName is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiDirectory getParentDirectory() {
    final PsiPackage psiPackage = myPackage.getParentPackage();
    return psiPackage == null ? null : new MockPsiDirectory(psiPackage, getProject());
  }

  @Override
  @NotNull
  public PsiDirectory[] getSubdirectories() {
    throw new UnsupportedOperationException("Method getSubdirectories is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return new LightVirtualFile();
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    throw new UnsupportedOperationException("Method processChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method setName is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkSetName is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public ItemPresentation getPresentation() {
    throw new UnsupportedOperationException("Method getPresentation is not yet implemented in " + getClass().getName());
  }
}
