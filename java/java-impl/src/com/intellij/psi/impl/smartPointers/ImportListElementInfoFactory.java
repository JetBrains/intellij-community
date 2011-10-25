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
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImportListElementInfoFactory implements SmartPointerElementInfoFactory {
  @Override
  @Nullable
  public SmartPointerElementInfo createElementInfo(@NotNull final PsiElement element) {
    if (element instanceof PsiImportList) {
      return new ImportListInfo((PsiJavaFile)element.getContainingFile());
    }
    return null;
  }

  private static class ImportListInfo extends FileElementInfo {
    public ImportListInfo(@NotNull PsiJavaFile file) {
      super(file);
    }

    @Override
    public PsiElement restoreElement() {
      PsiElement element = super.restoreElement();
      if (!(element instanceof PsiJavaFile)) return null;
      return ((PsiJavaFile)element).getImportList();
    }
  }
}
