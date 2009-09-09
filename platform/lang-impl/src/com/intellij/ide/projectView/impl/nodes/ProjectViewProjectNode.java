package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

public class ProjectViewProjectNode extends AbstractProjectNode {

  public ProjectViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    ProjectRootManager prm = ProjectRootManager.getInstance(getProject());
    ProjectFileIndex index = prm.getFileIndex();

    ArrayList<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();

    for (VirtualFile root : prm.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (parent == null || !index.isInContent(parent)) {
        nodes.add(new PsiDirectoryNode(getProject(), psiManager.findDirectory(root), getSettings()));
      }
    }


    final VirtualFile baseDir = getProject().getBaseDir();
    if (baseDir == null) return nodes;

    final VirtualFile[] files = baseDir.getChildren();
    for (VirtualFile file : files) {
      if (ModuleUtil.findModuleForFile(file, getProject()) == null) {
        if (!file.isDirectory()) {
          nodes.add(new PsiFileNode(getProject(), psiManager.findFile(file), getSettings()));
        }
      }
    }

    if (getSettings().isShowLibraryContents()) {
      nodes.add(new ExternalLibrariesNode(getProject(), getSettings()));
    }

    return nodes;
  }

  protected AbstractTreeNode createModuleGroup(final Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return createTreeNode(ProjectViewModuleNode.class, getProject(), module, getSettings());
  }

  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return createTreeNode(ProjectViewModuleGroupNode.class, getProject(), moduleGroup, getSettings());
  }
}
