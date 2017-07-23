/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 5/17/13
 */
public class LargeFilesAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PsiFile) {
      int length = element.getTextLength();
      int limit = PersistentFSConstants.getMaxIntellisenseFileSize();
      if (length > limit) {
        holder.createWarningAnnotation(element, "The file size (" + 
                                                StringUtil.formatFileSize(length) + ") " +
                                                "exceeds configured limit (" + 
                                                StringUtil.formatFileSize(limit) + "). " +
                                                "Code insight features are not available.").setFileLevelAnnotation(true);
      }
    }
  }
}
