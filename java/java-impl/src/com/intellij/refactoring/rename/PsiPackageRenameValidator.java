/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.rename;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * @since 14.03.2011
 */
public class PsiPackageRenameValidator implements RenameInputValidatorEx {
  private final ElementPattern<? extends PsiElement> myPattern = new MyPattern();

  private static class MyPattern extends PsiElementPattern<PsiPackage, MyPattern> {
    MyPattern() {
      super(new InitialPatternCondition<PsiPackage>(PsiPackage.class) {
        @Override
        public boolean accepts(@Nullable Object obj, ProcessingContext context) {
          if (!(obj instanceof PsiPackage)) {
            return false;
          }
          PsiPackage psiPackage = (PsiPackage)obj;
          // Check if the PsiPackage element points do a directory under a source root.
          ProjectRootManager rootManager = ProjectRootManager.getInstance(psiPackage.getProject());
          Set<VirtualFile> sourceRoots = new HashSet<>(rootManager.getModuleSourceRoots(JavaModuleSourceRootTypes.SOURCES));
          PsiDirectory[] packageDirectories = psiPackage.getDirectories();
          for (PsiDirectory packageDirectory : packageDirectories) {
            if (VfsUtilCore.isUnder(packageDirectory.getVirtualFile(), sourceRoots)) {
              return true;
            }
          }
          return false;
        }
      });
    }
  }

  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Nullable
  @Override
  public String getErrorMessage(@NotNull String newName, @NotNull Project project) {
    if (FileTypeManager.getInstance().isFileIgnored(newName)) {
      return "Trying to create a package with ignored name, result will not be visible";
    }

    if (!newName.isEmpty()) {
      if (!PsiDirectoryFactory.getInstance(project).isValidPackageName(newName)) {
        return "Not a valid package name";
      }
    }

    return null;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return !newName.isEmpty();
  }
}