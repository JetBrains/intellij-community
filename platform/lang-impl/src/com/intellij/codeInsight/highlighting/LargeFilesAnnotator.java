// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;

final class LargeFilesAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PsiFile) {
      VirtualFile file = ((PsiFile)element).getViewProvider().getVirtualFile();
      if (SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
        holder.newAnnotation(HighlightSeverity.WARNING, CodeInsightBundle.message("message.file.size.0.exceeds.code.insight.limit.1",
                                                                                  StringUtil.formatFileSize(file.getLength()),
                                                                                  StringUtil.formatFileSize(
                                                                                    FileSizeLimit.getIntellisenseLimit(file.getExtension()))))
          .fileLevel()
          .create();
      }
    }
  }
}
