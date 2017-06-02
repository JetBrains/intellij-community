/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class ExcludeFileIconLayerProvider implements IconLayerProvider {
  @Nullable
  @Override
  public Icon getLayerIcon(@NotNull Iconable element, boolean isLocked) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      ProjectFileIndex index = ProjectFileIndex.getInstance(((PsiFile)element).getProject());
      if (virtualFile != null && index.isExcluded(virtualFile)) {
        //If the parent directory is also excluded it'll have a special icon (see DirectoryIconProvider), so it makes no sense to add
        // additional marks for all files under it.
        if (!index.isExcluded(virtualFile.getParent())) {
          return AllIcons.Nodes.ExcludedFromCompile;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getLayerDescription() {
    return CodeInsightBundle.message("node.excluded.flag.tooltip");
  }
}
