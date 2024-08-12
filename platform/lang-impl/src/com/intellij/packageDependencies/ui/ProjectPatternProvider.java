// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public final class ProjectPatternProvider extends PatternDialectProvider {
  public static final @NonNls String FILE = "file";

  private static final Logger LOG = Logger.getInstance(ProjectPatternProvider.class);


  @Override
  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return FileTreeModelBuilder.createTreeModel(project, false, marker);
  }

  @Override
  public TreeModel createTreeModel(final Project project, final Set<? extends PsiFile> deps, final Marker marker,
                                   final DependenciesPanel.DependencyPanelSettings settings) {
    return FileTreeModelBuilder.createTreeModel(project, false, deps, marker, settings);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.project");
  }

  @Override
  public @NotNull String getShortName() {
    return FILE;
  }

  @Override
  public AnAction[] createActions(Project project, final Runnable update) {
    if (ProjectViewDirectoryHelper.getInstance(project).supportsHideEmptyMiddlePackages()) {
      return new AnAction[]{new CompactEmptyMiddlePackagesAction(update)};
    }
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public @Nullable PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    if (node instanceof ModuleGroupNode) {
      if (!recursively) return null;
      return new FilePatternPackageSet(getGroupModulePattern((ModuleGroupNode)node), "*//*");
    }
    else if (node instanceof ModuleNode) {
      if (!recursively) return null;
      final String modulePattern = ((ModuleNode)node).getModuleName();
      return new FilePatternPackageSet(modulePattern, "*/");
    }

    else if (node instanceof DirectoryNode) {
      String pattern = ((DirectoryNode)node).getFQName();
      if (pattern != null) {
        if (pattern.length() > 0) {
          pattern += recursively ? "//*" : "/*";
        }
        else {
          pattern += recursively ? "*/" : "*";
        }
      }
      final VirtualFile vDir = ((DirectoryNode)node).getDirectory();
      final PsiElement psiElement = node.getPsiElement();
      boolean projectFiles = true;
      String modulePattern = null;
      if (psiElement != null) {
        Project project = psiElement.getProject();
        final Module module = ModuleUtilCore.findModuleForFile(vDir, project);
        if (module == null) {
          projectFiles = false;
          modulePattern = ProjectFileIndex.getInstance(project)
            .getOrderEntriesForFile(vDir).stream()
            .filter(entry -> entry instanceof LibraryOrSdkOrderEntry)
            .findFirst()
            .map(entry -> entry instanceof JdkOrderEntry ? ((JdkOrderEntry)entry).getJdkName() : entry.getPresentableName())
            .orElse(null);
        }
        else {
          modulePattern = module.getName();
        }
      }
      
      return new FilePatternPackageSet(modulePattern, pattern, projectFiles);
    }
    else if (node instanceof LibraryNode) {
      return new FilePatternPackageSet(node.toString(), recursively ? "*/" : "*", false);
    }
    else if (node instanceof FileNode fNode) {
      if (recursively) return null;
      final PsiFile file = (PsiFile)fNode.getPsiElement();
      if (file == null) return null;
      final VirtualFile virtualFile = file.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final VirtualFile contentRoot = ProjectRootManager.getInstance(file.getProject()).getFileIndex().getContentRootForFile(virtualFile);
      if (contentRoot == null) return null;
      final String fqName = VfsUtilCore.getRelativePath(virtualFile, contentRoot, '/');
      if (fqName != null) return new FilePatternPackageSet(getModulePattern(node), fqName);
    }
    else if (node instanceof GeneralGroupNode) {//external dependencies
      return new FilePatternPackageSet("", recursively ? "*/" : "*", false);
    }
    return null;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }

  @Override
  public @Nls @NotNull String getHintMessage() {
    return LangBundle.message("package.pattern.provider.hint.label");
  }

  private static final class CompactEmptyMiddlePackagesAction extends ToggleAction {
    private final Runnable myUpdate;

    CompactEmptyMiddlePackagesAction(Runnable update) {
      super(IdeBundle.message("action.compact.empty.middle.packages"),
            IdeBundle.message("action.compact.empty.middle.packages"), AllIcons.ObjectBrowser.CompactEmptyPackages);
      myUpdate = update;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES = flag;
      myUpdate.run();
    }
  }
}
