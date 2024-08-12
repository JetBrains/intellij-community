// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ModuleGroupNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.*;

import static com.intellij.openapi.module.ModuleGrouperKt.isQualifiedModuleNamesEnabled;
import static java.awt.EventQueue.isDispatchThread;

public class ProjectViewPane extends AbstractProjectViewPaneWithAsyncSupport {
  public static final @NonNls String ID = "ProjectPane";

  public ProjectViewPane(Project project) {
    super(project);
  }

  @Override
  public void configureAsyncSupport(@NotNull AsyncProjectViewSupport support) {
    support.setMultiSelectionEnabled(false);
  }

  @Override
  public @NotNull String getTitle() {
    return IdeBundle.message("title.project");
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }


  @Override
  public @NotNull SelectInTarget createSelectInTarget() {
    return new ProjectPaneSelectInTarget(myProject);
  }

  @Override
  protected @NotNull ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectViewPaneTreeStructure();
  }

  @Override
  protected @NotNull ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      @Override
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      @Override
      public void setFont(Font font) {
        if (AdvancedSettings.getBoolean("bigger.font.in.project.view")) {
          font = font.deriveFont(font.getSize() + 1.0f);
        }
        super.setFont(font);
      }

      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = super.getAccessibleContext();
          accessibleContext.setAccessibleName(IdeBundle.message("project.structure.tree.accessible.name"));
        }
        return accessibleContext;
      }
    };
  }

  public @NotNull String getComponentName() {
    return "ProjectPane";
  }

  /**
   * @return {@code true} if 'Project View' have more than one top-level module node or have top-level module group nodes
   */
  private boolean hasSeveralTopLevelModuleNodes() {
    if (!isDispatchThread()) return true; // do not check nodes during building
    // TODO: have to rewrite this logic without using walking in a tree
    TreeModel treeModel = myTree.getModel();
    Object root = treeModel.getRoot();
    int count = treeModel.getChildCount(root);
    if (count <= 1) return false;
    int moduleNodes = 0;
    for (int i = 0; i < count; i++) {
      Object child = treeModel.getChild(root, i);
      if (child instanceof DefaultMutableTreeNode) {
        Object node = ((DefaultMutableTreeNode)child).getUserObject();
        if (node instanceof ProjectViewModuleNode || node instanceof PsiDirectoryNode) {
          moduleNodes++;
          if (moduleNodes > 1) {
            return true;
          }
        }
        else if (node instanceof ModuleGroupNode) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isFileNestingEnabled() {
    return true;
  }

  // should be first
  @Override
  public int getWeight() {
    return 0;
  }

  protected class ProjectViewPaneTreeStructure extends ProjectTreeStructure implements ProjectViewSettings {
    protected ProjectViewPaneTreeStructure() {
      super(ProjectViewPane.this.myProject, ProjectViewPane.this.getId());
    }

    @Override
    protected AbstractTreeNode<?> createRoot(@NotNull Project project, @NotNull ViewSettings settings) {
      return new ProjectViewProjectNode(project, settings);
    }

    @Override
    public boolean isShowExcludedFiles() {
      return ProjectView.getInstance(myProject).isShowExcludedFiles(getId());
    }

    @Override
    public boolean isShowLibraryContents() {
      return true;
    }

    @Override
    public boolean isUseFileNestingRules() {
      return ProjectView.getInstance(myProject).isUseFileNestingRules(getId());
    }

    @Override
    public boolean isToBuildChildrenInBackground(@NotNull Object element) {
      return Registry.is("ide.projectView.ProjectViewPaneTreeStructure.BuildChildrenInBackground");
    }
  }

  public static boolean canBeSelectedInProjectView(@NotNull Project project, @NotNull VirtualFile file) {
    final VirtualFile archiveFile;

    if(file.getFileSystem() instanceof ArchiveFileSystem)
      archiveFile = ((ArchiveFileSystem)file.getFileSystem()).getLocalByEntry(file);
    else
      archiveFile = null;

    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return (archiveFile != null && index.getContentRootForFile(archiveFile, false) != null) ||
           index.getContentRootForFile(file, false) != null ||
           index.isInLibrary(file) ||
           Comparing.equal(file.getParent(), project.getBaseDir()) ||
           (ScratchUtil.isScratch(file) && ProjectView.getInstance(project).isShowScratchesAndConsoles(ID));
  }

  @Override
  public boolean supportsFlattenModules() {
    return PlatformUtils.isIntelliJ() && isQualifiedModuleNamesEnabled(myProject) && hasSeveralTopLevelModuleNodes();
  }

  @Override
  public boolean supportsShowExcludedFiles() {
    return true;
  }

  @Override
  public boolean supportsShowScratchesAndConsoles() {
    return true;
  }
}
