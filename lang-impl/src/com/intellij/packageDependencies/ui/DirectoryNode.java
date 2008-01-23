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
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class DirectoryNode extends PackageDependenciesNode {

  private String myDirName;
  private PsiDirectory myDirectory;

  private DirectoryNode myCompactedDirNode;
  private DirectoryNode myWrapper;

  private boolean myCompactPackages = true;
  private String myFQName = null;
  //private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.ui.DirectoryNode");

  public DirectoryNode(PsiDirectory aDirectory, boolean compactPackages, boolean showFQName) {
    myDirectory = aDirectory;
    VirtualFile directory = myDirectory.getVirtualFile();
    final ProjectFileIndex index = ProjectRootManager.getInstance(myDirectory.getProject()).getFileIndex();
    myDirName = aDirectory.getName();
    if (showFQName) {
      final VirtualFile contentRoot = index.getContentRootForFile(directory);
      if (contentRoot != null) {
        if (directory == contentRoot) {
          myFQName = myDirName;
        }
        else {
          final VirtualFile sourceRoot = index.getSourceRootForFile(directory);
          if (directory == sourceRoot) {
            myFQName = myDirName;
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
      myDirName = myFQName;
    }
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
    return Icons.PACKAGE_OPEN_ICON;
  }

  public Icon getClosedIcon() {
    return Icons.PACKAGE_ICON;
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
  public String getComment(final boolean forceLocation) {
    if (myDirectory != null && myDirectory.isValid()) {
      return ProjectViewDirectoryHelper.getInstance(myDirectory.getProject()).getLocationString(myDirectory, forceLocation);
    }
    return super.getComment(forceLocation);
  }
}
