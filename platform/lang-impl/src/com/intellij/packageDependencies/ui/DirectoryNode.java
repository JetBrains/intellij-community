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
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public class DirectoryNode extends PackageDependenciesNode {

  private final String myDirName;
  private final PsiDirectory myDirectory;

  private DirectoryNode myCompactedDirNode;
  private DirectoryNode myWrapper;

  private boolean myCompactPackages = true;
  private String myFQName = null;
  //private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.ui.DirectoryNode");

  public DirectoryNode(PsiDirectory aDirectory, boolean compactPackages, boolean showFQName) {
    myDirectory = aDirectory;
    VirtualFile directory = myDirectory.getVirtualFile();
    final ProjectFileIndex index = ProjectRootManager.getInstance(myDirectory.getProject()).getFileIndex();
    String dirName = aDirectory.getName();
    if (showFQName) {
      final VirtualFile contentRoot = index.getContentRootForFile(directory);
      if (contentRoot != null) {
        if (directory == contentRoot) {
          myFQName = dirName;
        }
        else {
          final VirtualFile sourceRoot = index.getSourceRootForFile(directory);
          if (directory == sourceRoot) {
            myFQName = dirName;
          }
          else if (sourceRoot != null) {
            myFQName = VfsUtil.getRelativePath(directory, sourceRoot, '/');
          }
          else {
            myFQName = VfsUtil.getRelativePath(directory, contentRoot, '/');
          }
        }
      }
      else {
        myFQName = FilePatternPackageSet.getLibRelativePath(directory, index);
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
    if (myDirectory == null || !myDirectory.isValid()) return "";
    if (myCompactPackages && myCompactedDirNode != null) {
      return myDirectory.getName() + "/" + myCompactedDirNode.getDirName();
    }
    return myDirName;
  }

  public String getFQName() {
    final StringBuffer buf = new StringBuffer();
    final Project project = myDirectory.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile directory = myDirectory.getVirtualFile();
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
    return myDirectory;
  }

  public PsiDirectory getTargetDirectory() {
    DirectoryNode dirNode = this;
    while (dirNode.getCompactedDirNode() != null) {
      dirNode = dirNode.getCompactedDirNode();
      assert dirNode != null;
    }

    return dirNode.myDirectory;
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
    return myDirectory != null && myDirectory.isValid();
  }

  public boolean canNavigate() {
    return false;
  }

  @Override
  public String getComment() {
    if (myDirectory != null && myDirectory.isValid()) {
      return ProjectViewDirectoryHelper.getInstance(myDirectory.getProject()).getLocationString(myDirectory);
    }
    return super.getComment();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    Set<PsiFile> files = deps.keySet();
    for (PsiFile file : files) {
      if (file.getContainingDirectory() == myDirectory) {
        return true;
      }
    }
    return false;
  }
}
