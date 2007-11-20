package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIncludeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class FileIncludeContextHectorProvider implements HectorComponentPanelsProvider {
  private final PsiIncludeManager myIncludeManager;

  public FileIncludeContextHectorProvider(final PsiIncludeManager includeManager) {
    myIncludeManager = includeManager;
  }


  @Nullable
  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    if (myIncludeManager.getIncludingFiles(file).length > 0) {
      return new FileIncludeContextHectorPanel(file, myIncludeManager);
    }

    return null;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileIncludeContextHectorProvider";
  }

  public void initComponent() { }

  public void disposeComponent() { }
}
