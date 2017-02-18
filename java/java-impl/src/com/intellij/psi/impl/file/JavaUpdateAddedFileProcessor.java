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
package com.intellij.psi.impl.file;

import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 * @since Sep 18, 2008
 */
public class JavaUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  @Override
  public boolean canProcessElement(@NotNull final PsiFile file) {
    return file instanceof PsiClassOwner && !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file);
  }

  @Override
  public void update(final PsiFile element, PsiFile originalElement) throws IncorrectOperationException {
    if (element.getViewProvider() instanceof TemplateLanguageFileViewProvider || PsiUtil.isModuleFile(element)) {
      return;
    }

    PsiDirectory dir = element.getContainingDirectory();
    if (dir != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
      if (aPackage != null) {
        String packageName = aPackage.getQualifiedName();
        ((PsiClassOwner)element).setPackageName(packageName);
      }
    }
  }
}