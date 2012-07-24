/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
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

  public DirectoryNode(VirtualFile aDirectory,
                       Project project,
                       boolean compactPackages,
                       boolean showFQName,
                       VirtualFile baseDir, final VirtualFile[] contentRoots) {
    super(project);
    myVDirectory = aDirectory;
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    final ProjectFileIndex index = projectRootManager.getFileIndex();
    String dirName = aDirectory.getName();
    if (showFQName) {
      final VirtualFile contentRoot = index.getContentRootForFile(myVDirectory);
      if (contentRoot != null) {
        if (Comparing.equal(myVDirectory, contentRoot)) {
          myFQName = dirName;
        }
        else {
          final VirtualFile sourceRoot = index.getSourceRootForFile(myVDirectory);
          if (Comparing.equal(myVDirectory, sourceRoot)) {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, contentRoot, '/');
          }
          else if (sourceRoot != null) {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, sourceRoot, '/');
          }
          else {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, contentRoot, '/');
          }
        }

        if (contentRoots.length > 1 && ProjectRootsUtil.isModuleContentRoot(myVDirectory, project)) {
          myFQName = getContentRootName(baseDir, myFQName);
        }
      }
      else {
        myFQName = FilePatternPackageSet.getLibRelativePath(myVDirectory, index);
      }
      dirName = myFQName;
    } else {
      if (contentRoots.length > 1 && ProjectRootsUtil.isModuleContentRoot(myVDirectory, project)) {
        dirName = getContentRootName(baseDir, dirName);
      }
    }
    myDirName = dirName;
    myCompactPackages = compactPackages;
  }

  private String getContentRootName(final VirtualFile baseDir, final String dirName) {
    if (baseDir != null) {
      if (!Comparing.equal(myVDirectory, baseDir)) {
        if (VfsUtil.isAncestor(baseDir, myVDirectory, false)) {
          return VfsUtilCore.getRelativePath(myVDirectory, baseDir, '/');
        }
        else {
          return myVDirectory.getPresentableUrl();
        }
      }
    } else {
      return myVDirectory.getPresentableUrl();
    }
    return dirName;
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
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile directory = myVDirectory;
    VirtualFile contentRoot = index.getContentRootForFile(directory);
    if (Comparing.equal(directory, contentRoot)) {
      return "";
    }
    if (contentRoot == null) {
      return "";
    }
    return VfsUtilCore.getRelativePath(directory, contentRoot, '/');
  }

  public PsiElement getPsiElement() {
    return getPsiDirectory();
  }

  @Nullable
  private PsiDirectory getPsiDirectory() {
    if (myDirectory == null) {
      if (myVDirectory.isValid() && !myProject.isDisposed()) {
        myDirectory = PsiManager.getInstance(myProject).findDirectory(myVDirectory);
      }
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
    if (myVDirectory != null && myVDirectory.isValid() && !myProject.isDisposed()) {
      final PsiDirectory directory = getPsiDirectory();
      if (directory != null) {
        return ProjectViewDirectoryHelper.getInstance(myProject).getLocationString(directory);
      }
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
