/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PsiIconUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileIconPatcherImpl implements FileIconProvider {
  @Nullable
  public Icon getIcon(final VirtualFile file, final int flags, final Project project) {
    if(project == null) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile == null ? null : PsiIconUtil.getProvidersIcon(psiFile, flags);
  }
}