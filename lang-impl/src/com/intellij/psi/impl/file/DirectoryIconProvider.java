/*
 * User: anna
 * Date: 23-Jan-2008
 */
package com.intellij.psi.impl.file;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DirectoryIconProvider implements IconProvider{
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      boolean inTestSource = ProjectRootsUtil.isInTestSource(vFile, psiDirectory.getProject());
      boolean isSourceOrTestRoot = ProjectRootsUtil.isSourceOrTestRoot(vFile, psiDirectory.getProject());
      final boolean isOpen = (flags & Iconable.ICON_FLAG_OPEN) != 0;
      if (isSourceOrTestRoot) {
        return IconSet.getSourceRootIcon(inTestSource, isOpen);
      }
      else {
        return isOpen ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
      }
    }
    return null;
  }
}