// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;

public class PackagesPaneSelectInTarget extends ProjectViewSelectInTarget implements DumbAware {
  public PackagesPaneSelectInTarget(Project project) {
    super(project);
  }

  @Override
  public String toString() {
    return IdeBundle.message("select.in.packages");
  }

  @Override
  public boolean canSelect(PsiFileSystemItem file) {
    VirtualFile vFile = PsiUtilCore.getVirtualFile(file);
    vFile = vFile == null ? null : BackedVirtualFile.getOriginFileIfBacked(vFile);
    if (vFile == null || !vFile.isValid()) return false;

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    return fileIndex.isInSourceContent(vFile) ||
           isInLibraryContentOnly(vFile);
  }

  @Override
  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    return canSelect(context);
  }

  private boolean isInLibraryContentOnly(final VirtualFile vFile) {
    if (vFile == null) {
      return false;
    }
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    return projectFileIndex.isInLibrary(vFile) && !projectFileIndex.isInSourceContent(vFile);
  }

  @Override
  public String getMinorViewId() {
    return PackageViewPane.ID;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.PACKAGES_WEIGHT;
  }

}
