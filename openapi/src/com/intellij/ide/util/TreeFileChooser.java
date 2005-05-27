package com.intellij.ide.util;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Shows dialog with two tabs: Project View-like tree and Goto symbol-like list with quick search capability
 * Allows to quickly locate and choose PsiFile among all files inside project
 * (optionally filtered based on file type or general file filter(see PsiFileFilter))
 * @see TreeClassChooserFactory#createFileChooser(String, PsiFile, FileType, PsiFileFilter)
 * @see PsiFileFilter
 */
public interface TreeFileChooser {
  /**
   * @return null when no files were selected or dialog has been canceled
   */
  @Nullable PsiFile getSelectedFile();

  /**
   * @param file to be selected in tree view tab of this dialog
   */
  void selectFile(@NotNull PsiFile file);

  void showDialog();

  interface PsiFileFilter {
    boolean accept(PsiFile file);
  }
}
