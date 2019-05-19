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

package com.intellij.util;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

public class PsiNavigateUtil {
  public static void navigate(@Nullable final PsiElement psiElement) {
    if (psiElement != null && psiElement.isValid()) {
      final PsiElement navigationElement = psiElement.getNavigationElement();
      final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();

      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
      Navigatable navigatable;
      if (virtualFile != null && virtualFile.isValid()) {
        navigatable = PsiNavigationSupport.getInstance().createNavigatable(navigationElement.getProject(), virtualFile, offset);
      }
      else if (navigationElement instanceof Navigatable) {
        navigatable = (Navigatable)navigationElement;
      }
      else {
        return;
      }
      navigatable.navigate(true);
    }
  }
}