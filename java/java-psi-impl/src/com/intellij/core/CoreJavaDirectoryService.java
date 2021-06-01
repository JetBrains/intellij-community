// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class CoreJavaDirectoryService extends JavaDirectoryService {
  private static final Logger LOG = Logger.getInstance(CoreJavaDirectoryService.class);

  @Override
  public PsiPackage getPackage(@NotNull PsiDirectory dir) {
    return dir.getProject().getService(CoreJavaFileManager.class).getPackage(dir);
  }

  @Nullable
  @Override
  public PsiPackage getPackageInSources(@NotNull PsiDirectory dir) {
    return getPackage(dir);
  }

  @Override
  public PsiClass @NotNull [] getClasses(@NotNull PsiDirectory dir) {
    LOG.assertTrue(dir.isValid());
    return getPsiClasses(dir, dir.getFiles());
  }

  public static PsiClass @NotNull [] getPsiClasses(@NotNull PsiDirectory dir, PsiFile[] psiFiles) {
    FileIndexFacade index = FileIndexFacade.getInstance(dir.getProject());
    VirtualFile virtualDir = dir.getVirtualFile();
    boolean onlyCompiled = index.isInLibraryClasses(virtualDir) && !index.isInSourceContent(virtualDir);

    List<PsiClass> classes = null;
    for (PsiFile file : psiFiles) {
      if (onlyCompiled && !(file instanceof ClsFileImpl)) {
        continue;
      }
      if (file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1) {
        PsiClass[] psiClasses = ((PsiClassOwner)file).getClasses();
        if (psiClasses.length == 0) continue;
        if (classes == null) classes = new ArrayList<>();
        ContainerUtil.addAll(classes, psiClasses);
      }
    }
    return classes == null ? PsiClass.EMPTY_ARRAY : classes.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name, @NotNull String templateName)
    throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiClass createClass(@NotNull PsiDirectory dir,
                              @NotNull String name,
                              @NotNull String templateName,
                              boolean askForUndefinedVariables) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiClass createClass(@NotNull PsiDirectory dir,
                              @NotNull String name,
                              @NotNull String templateName,
                              boolean askForUndefinedVariables, @NotNull final Map<String, String> additionalProperties) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkCreateClass(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiClass createInterface(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiClass createEnum(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiClass createRecord(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiClass createAnnotationType(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSourceRoot(@NotNull PsiDirectory dir) {
    return false;
  }

  @Override
  public LanguageLevel getLanguageLevel(@NotNull PsiDirectory dir) {
    return LanguageLevel.HIGHEST;
  }
}
