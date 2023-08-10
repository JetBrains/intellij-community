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

import com.intellij.java.JavaBundle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public class PsiPackageRenameValidator implements RenameInputValidatorEx {
  private final ElementPattern<? extends PsiElement> myPattern = PlatformPatterns.psiElement(PsiPackage.class);

  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Nullable
  @Override
  public String getErrorMessage(@NotNull String newName, @NotNull Project project) {
    if (FileTypeManager.getInstance().isFileIgnored(newName)) {
      return JavaBundle.message("rename.package.ignored.name.warning");
    }

    if (newName.length() > 0) {
      if (!PsiDirectoryFactory.getInstance(project).isValidPackageName(newName)) {
        return JavaBundle.message("rename.package.invalid.name.error");
      }
    }

    return null;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return !newName.isEmpty();
  }
}