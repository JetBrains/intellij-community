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

package com.intellij.packageDependencies.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IconUtil;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public class FileNode extends PackageDependenciesNode {
  private final PsiFile myFile;
  private final boolean myMarked;
  private static final Logger LOG = Logger.getInstance("com.intellij.packageDependencies.ui.FileNode");

  public FileNode(PsiFile file, boolean marked) {
    myFile = file;
    myMarked = marked;    
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    set.add(myFile);
  }

  public boolean hasUnmarked() {
    return !myMarked;
  }

  public boolean hasMarked() {
    return myMarked;
  }

  public String toString() {
    final VirtualFile virtualFile = myFile.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    return virtualFile.getName();
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    VirtualFile vFile = myFile.getVirtualFile();
    LOG.assertTrue(vFile != null);
    return IconUtil.getIcon(vFile, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, myFile.getProject());
  }

  public int getWeight() {
    return 5;
  }

  public int getContainingFiles() {
    return 1;
  }

  public PsiElement getPsiElement() {
    return myFile;
  }

  public FileStatus getStatus() {
    return FileStatusManager.getInstance(myFile.getProject()).getStatus(myFile.getVirtualFile());
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof FileNode)) return false;

    final FileNode fileNode = (FileNode)o;

    if (!myFile.equals(fileNode.myFile)) return false;

    return true;
  }

  public int hashCode() {
    return myFile.hashCode();
  }


  public boolean isValid() {
    return myFile != null && myFile.isValid();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    return deps.containsKey(myFile);
  }
}
