package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
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

  @Nullable
  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    if (myIncludeManager.getIncludingFiles(file.getVirtualFile(), false).length > 0) {
      return new FileIncludeContextHectorPanel(file, myIncludeManager);
    }
    return null;
  }

}
