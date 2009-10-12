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

package com.intellij.ide.util;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.Nullable;

public class EditSourceUtil {
  private EditSourceUtil() {
  }

  @Nullable
  public static Navigatable getDescriptor(final PsiElement element) {
    if (!canNavigate(element)) {
      return null;
    }
    final PsiElement navigationElement = element.getNavigationElement();
    final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(navigationElement);
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }
    return new OpenFileDescriptor(navigationElement.getProject(), virtualFile, offset);
  }

  public static boolean canNavigate (PsiElement element) {
    if (element == null || !element.isValid()) {
      return false;
    }
    final PsiElement navigationElement = element.getNavigationElement();
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(navigationElement);
    return virtualFile != null && virtualFile.isValid();
  }
}