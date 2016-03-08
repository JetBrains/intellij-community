/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class CoreJavaDirectoryService extends JavaDirectoryService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.CoreJavaDirectoryService");

  @Override
  public PsiPackage getPackage(@NotNull PsiDirectory dir) {
    return ServiceManager.getService(dir.getProject(), CoreJavaFileManager.class).getPackage(dir);
  }

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiDirectory dir) {
    LOG.assertTrue(dir.isValid());
    return getPsiClasses(dir, dir.getFiles());
  }

  @NotNull
  public static PsiClass[] getPsiClasses(@NotNull PsiDirectory dir, PsiFile[] psiFiles) {
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
        if (classes == null) classes = new ArrayList<PsiClass>();
        ContainerUtil.addAll(classes, psiClasses);
      }
    }
    return classes == null ? PsiClass.EMPTY_ARRAY : classes.toArray(new PsiClass[classes.size()]);
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
