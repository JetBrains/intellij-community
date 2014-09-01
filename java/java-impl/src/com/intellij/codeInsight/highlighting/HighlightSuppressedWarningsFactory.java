/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class HighlightSuppressedWarningsFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(target, PsiAnnotation.class);
    if (annotation != null && Comparing.strEqual(SuppressWarnings.class.getName(), annotation.getQualifiedName())) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null && !virtualFile.getFileType().isBinary()) {
        return new HighlightSuppressedWarningsHandler(editor, file, annotation, PsiTreeUtil.getParentOfType(target, PsiLiteralExpression.class));
      }
    }
    return null;
  }
}