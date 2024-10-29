// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Set;

public final class FileNode extends PackageDependenciesNode implements Comparable<FileNode>{
  private final VirtualFile myVFile;
  private final boolean myMarked;
  private Icon myIcon;

  public FileNode(VirtualFile file, Project project, boolean marked) {
    super(project);
    myVFile = file;
    myMarked = marked;
  }

  @Override
  public void update() {
    super.update();
    myIcon = doGetIcon();
  }

  private @NotNull Icon doGetIcon() {
    return IconUtil.getIcon(myVFile, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, myProject);
  }

  @Override
  public void fillFiles(Set<? super PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    final PsiFile file = getFile();
    if (file != null && file.isValid()) {
      set.add(file);
    }
  }

  @Override
  public boolean hasUnmarked() {
    return !myMarked;
  }

  @Override
  public boolean hasMarked() {
    return myMarked;
  }

  public String toString() {
    return myVFile.getName();
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public int getWeight() {
    return 5;
  }

  @Override
  public int getContainingFiles() {
    return 1;
  }

  @Override
  public PsiElement getPsiElement() {
    return getFile();
  }

  @Override
  public Color getColor() {
    if (myColor == null) {
      myColor = FileStatusManager.getInstance(myProject).getStatus(myVFile).getColor();
      if (myColor == null) {
        myColor = NOT_CHANGED;
      }
    }
    return myColor == NOT_CHANGED ? null : myColor;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof FileNode fileNode)) return false;

    if (!myVFile.equals(fileNode.myVFile)) return false;

    return true;
  }

  public int hashCode() {
    return myVFile.hashCode();
  }


  @Override
  public boolean isValid() {
    return myVFile != null && myVFile.isValid();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    return deps.containsKey(getFile());
  }

  private @Nullable PsiFile getFile() {
    return myVFile.isValid() && !myProject.isDisposed() ? PsiManager.getInstance(myProject).findFile(myVFile) : null;
  }

  @Override
  public int compareTo(FileNode o) {
    final int compare = StringUtil.compare(myVFile != null ? myVFile.getFileType().getDefaultExtension() : null,
                                           o.myVFile != null ? o.myVFile.getFileType().getDefaultExtension() : null,
                                           true);
    if (compare != 0) return compare;
    return StringUtil.compare(toString(), o.toString(), true);
  }
}
