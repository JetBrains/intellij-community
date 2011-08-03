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

import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public class DirectoryNode extends PackageDependenciesNode {

  private final String myDirName;
  private PsiDirectory myDirectory;

  private DirectoryNode myCompactedDirNode;
  private DirectoryNode myWrapper;

  private boolean myCompactPackages = true;
  private String myFQName = null;
  private final VirtualFile myVDirectory;

  public DirectoryNode(VirtualFile aDirectory, Project project, boolean compactPackages, boolean showFQName) {
    super(project);
    myVDirectory = aDirectory;
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    String dirName = aDirectory.getName();
    if (showFQName) {
      final VirtualFile contentRoot = index.getContentRootForFile(myVDirectory);
      if (contentRoot != null) {
        if (myVDirectory == contentRoot) {
          myFQName = dirName;
        }
        else {
          final VirtualFile sourceRoot = index.getSourceRootForFile(myVDirectory);
          if (myVDirectory == sourceRoot) {
            myFQName = VfsUtil.getRelativePath(myVDirectory, contentRoot, '/');
          }
          else if (sourceRoot != null) {
            myFQName = VfsUtil.getRelativePath(myVDirectory, sourceRoot, '/');
          }
          else {
            myFQName = VfsUtil.getRelativePath(myVDirectory, contentRoot, '/');
          }
        }
      }
      else {
        myFQName = FilePatternPackageSet.getLibRelativePath(myVDirectory, index);
      }
      dirName = myFQName;
    }
    myDirName = dirName;
    myCompactPackages = compactPackages;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      if (child instanceof FileNode || recursively) {
        child.fillFiles(set, true);
      }
    }
  }

  public String toString() {
    if (myFQName != null) return myFQName;
    if (myCompactPackages && myCompactedDirNode != null) {
      return myDirName + "/" + myCompactedDirNode.getDirName();
    }
    return myDirName;
  }

  public String getDirName() {
    if (myVDirectory == null || !myVDirectory.isValid()) return "";
    if (myCompactPackages && myCompactedDirNode != null) {
      return myVDirectory.getName() + "/" + myCompactedDirNode.getDirName();
    }
    return myDirName;
  }

  public String getFQName() {
    final StringBuffer buf = new StringBuffer();
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile directory = myVDirectory;
    VirtualFile contentRoot = index.getContentRootForFile(directory);
    if (directory == contentRoot) {
      return "";
    }
    if (contentRoot == null) {
      return "";
    }
    while (directory != null && contentRoot != directory) {
      buf.insert(0, directory.getName() + "/");
      directory = directory.getParent();
    }
    return buf.toString();
  }

  public PsiElement getPsiElement() {
    return getPsiDirectory();
  }

  @Nullable
  private PsiDirectory getPsiDirectory() {
    if (myDirectory == null) {
      myDirectory = PsiManager.getInstance(myProject).findDirectory(myVDirectory);
    }
    return myDirectory;
  }

  public PsiDirectory getTargetDirectory() {
    DirectoryNode dirNode = this;
    while (dirNode.getCompactedDirNode() != null) {
      dirNode = dirNode.getCompactedDirNode();
      assert dirNode != null;
    }

    return dirNode.getPsiDirectory();
  }

  public int getWeight() {
    return 3;
  }

  public boolean equals(Object o) {
    if (isEquals()) {
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof DirectoryNode)) return false;

    final DirectoryNode packageNode = (DirectoryNode)o;

    if (!toString().equals(packageNode.toString())) return false;

    return true;
  }

  public int hashCode() {
    return toString().hashCode();
  }

  public Icon getOpenIcon() {
    return PlatformIcons.PACKAGE_OPEN_ICON;
  }

  public Icon getClosedIcon() {
    return PlatformIcons.PACKAGE_ICON;
  }

  public void setCompactedDirNode(final DirectoryNode compactedDirNode) {
    if (myCompactedDirNode != null) {
      myCompactedDirNode.myWrapper = null;
    }
    myCompactedDirNode = compactedDirNode;
    if (myCompactedDirNode != null) {
      myCompactedDirNode.myWrapper = this;
    }
  }

  public DirectoryNode getWrapper() {
    return myWrapper;
  }

  @Nullable
  public DirectoryNode getCompactedDirNode() {
    return myCompactPackages ? myCompactedDirNode : null;
  }

  public void removeUpReference() {
    myWrapper = null;
  }


  public boolean isValid() {
    return myVDirectory != null && myVDirectory.isValid();
  }

  public boolean canNavigate() {
    return false;
  }

  @Override
  public String getComment() {
    if (myVDirectory != null && myVDirectory.isValid()) {
      return ProjectViewDirectoryHelper.getInstance(myProject).getLocationString(getPsiDirectory());
    }
    return super.getComment();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    Set<PsiFile> files = deps.keySet();
    for (PsiFile file : files) {
      if (file.getContainingDirectory() == getPsiDirectory()) {
        return true;
      }
    }
    return false;
  }

  public VirtualFile getDirectory() {
    return myVDirectory;
  }
}
