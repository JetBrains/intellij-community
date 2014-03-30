/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


public class JavaProblemHighlightFilter extends ProblemHighlightFilter {
  @Override
  public boolean shouldHighlight(@NotNull PsiFile psiFile) {
    return psiFile.getFileType() != StdFileTypes.JAVA || !JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile);
  }

  @Override
  public boolean shouldProcessInBatch(@NotNull PsiFile psiFile) {
    final boolean shouldHighlight = shouldHighlightFile(psiFile);
    if (shouldHighlight) {
      if (psiFile.getFileType() == StdFileTypes.JAVA) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null && ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex().isInLibrarySource(virtualFile)) {
          return false;
        }
      }
    }
    return shouldHighlight;
  }
}
