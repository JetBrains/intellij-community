/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class OpenSourceUtil {

  private OpenSourceUtil() {
  }

  public static void openSourcesFrom(DataContext context, boolean requestFocus) {
    navigate((Navigatable[])context.getData(DataConstants.NAVIGATABLE_ARRAY), requestFocus);
  }

  public static void openSourcesFrom(DataProvider context, boolean requestFocus) {
    navigate((Navigatable[])context.getData(DataConstants.NAVIGATABLE_ARRAY), requestFocus);
  }

  public static void navigate(final Navigatable[] navigatables, final boolean requestFocus) {
    if (navigatables != null) {
      for (Navigatable navigatable : navigatables) {
        if (navigatable.canNavigate()) {
          navigatable.navigate(requestFocus);
        }
      }
    }
  }

  public static void navigate(@Nullable final PsiElement psiElement) {
    if (psiElement != null && psiElement.isValid()) {
      final PsiElement navigationElement = psiElement.getNavigationElement();
      final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
      final VirtualFile virtualFile = navigationElement.getContainingFile().getVirtualFile();
      if (virtualFile != null && virtualFile.isValid()) {
        new OpenFileDescriptor(navigationElement.getProject(), virtualFile, offset).navigate(true);
      }
    }
  }
}
