// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class JavaProblemHighlightFilter extends ProblemHighlightFilter {

  private static final Logger LOG = Logger.getInstance(JavaProblemHighlightFilter.class);

  @Override
  public boolean shouldHighlight(@NotNull PsiFile psiFile) {
    var psiFileType = psiFile.getFileType();
    var isOutsideJavaSourceRoot = JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile);
    var isScratch = ScratchUtil.isScratch(psiFile.getVirtualFile());
    var isJavaHashBangScript = JavaHighlightUtil.isJavaHashBangScript(psiFile);

    if (ApplicationManagerEx.isInIntegrationTest()) {
      LOG.debug(
        "JavaProblemHighlightFilter status: ", psiFileType,
        " isOutsideJavaSourceRoot ", isOutsideJavaSourceRoot,
        " isScratch ", isScratch,
        " isJavaHashBangScript ", isJavaHashBangScript);
    }

    return psiFileType != JavaFileType.INSTANCE ||
           !isOutsideJavaSourceRoot ||
           isScratch ||
           isJavaHashBangScript;
  }

  @Override
  public boolean shouldProcessInBatch(@NotNull PsiFile psiFile) {
    final boolean shouldHighlight = shouldHighlightFile(psiFile);
    if (shouldHighlight) {
      if (psiFile.getFileType() == JavaFileType.INSTANCE) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
          if (fileIndex.isInLibrarySource(virtualFile)) {
            return fileIndex.isInSourceContent(virtualFile);
          }
        }
      }
    }
    return shouldHighlight;
  }
}
