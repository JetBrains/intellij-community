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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.include.FileIncludeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class FileIncludeContextHectorProvider implements HectorComponentPanelsProvider {
  private final FileIncludeManager myIncludeManager;

  public FileIncludeContextHectorProvider(final FileIncludeManager includeManager) {
    myIncludeManager = includeManager;
  }

  @Override
  @Nullable
  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    if (DumbService.getInstance(file.getProject()).isDumb()) {
      return null;
    }
    if (myIncludeManager.getIncludingFiles(file.getVirtualFile(), false).length > 0) {
      return new FileIncludeContextHectorPanel(file, myIncludeManager);
    }
    return null;
  }

}
