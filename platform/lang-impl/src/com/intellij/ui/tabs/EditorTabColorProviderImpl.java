package com.intellij.ui.tabs;

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FileColorManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author spleaner
 */
public class EditorTabColorProviderImpl implements EditorTabColorProvider {

  @Nullable
  public Color getEditorTabColor(Project project, VirtualFile file) {
    final FileColorManager colorManager = FileColorManagerImpl.getInstance(project);
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile != null && colorManager.isEnabledForTabs() ? colorManager.getFileColor(psiFile) : null;
  }
}
