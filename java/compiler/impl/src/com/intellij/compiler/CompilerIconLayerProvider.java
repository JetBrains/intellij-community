// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class CompilerIconLayerProvider implements IconLayerProvider {
  @Override
  public Icon getLayerIcon(@NotNull Iconable element, boolean isLocked) {
    VirtualFile vFile = null;
    Project project = null;
    if (element instanceof PsiModifierListOwner) {
      project = ((PsiModifierListOwner) element).getProject();
      final PsiFile containingFile = ((PsiModifierListOwner) element).getContainingFile();
      vFile = containingFile == null ? null : containingFile.getVirtualFile();
    }
    else if (element instanceof PsiDirectory) {
      project = ((PsiDirectory) element).getProject();
      vFile = ((PsiDirectory) element).getVirtualFile();
    }
    if (vFile != null && isExcluded(vFile, project)) {
      return PlatformIcons.EXCLUDED_FROM_COMPILE_ICON;
    }
    return null;
  }

  @NotNull
  @Override
  public String getLayerDescription() {
    return JavaCompilerBundle.message("node.excluded.flag.tooltip");
  }

  public static boolean isExcluded(final VirtualFile vFile, final Project project) {
    return vFile != null
           && FileIndexFacade.getInstance(project).isInSource(vFile)
           && CompilerConfiguration.getInstance(project).isExcludedFromCompilation(vFile);
  }
}
