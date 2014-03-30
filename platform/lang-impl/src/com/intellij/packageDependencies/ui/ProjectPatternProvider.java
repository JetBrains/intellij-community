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

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class ProjectPatternProvider extends PatternDialectProvider {

  @NonNls public static final String FILE = "file";

  private static final Logger LOG = Logger.getInstance("#" + ProjectPatternProvider.class.getName());


  @Override
  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return FileTreeModelBuilder.createTreeModel(project, false, marker);
  }

  @Override
  public TreeModel createTreeModel(final Project project, final Set<PsiFile> deps, final Marker marker,
                                   final DependenciesPanel.DependencyPanelSettings settings) {
    return FileTreeModelBuilder.createTreeModel(project, false, deps, marker, settings);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.project");
  }

  @Override
  @NotNull
  public String getShortName() {
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
  @Nullable
  public PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    if (node instanceof ModuleGroupNode) {
      if (!recursively) return null;
      @NonNls final String modulePattern = "group:" + ((ModuleGroupNode)node).getModuleGroup().toString();
      return new FilePatternPackageSet(modulePattern, "*//*");
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
      return new FilePatternPackageSet(getModulePattern(node), pattern);
    }
    else if (node instanceof FileNode) {
      if (recursively) return null;
      FileNode fNode = (FileNode)node;
      final PsiFile file = (PsiFile)fNode.getPsiElement();
      if (file == null) return null;
      final VirtualFile virtualFile = file.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final VirtualFile contentRoot = ProjectRootManager.getInstance(file.getProject()).getFileIndex().getContentRootForFile(virtualFile);
      if (contentRoot == null) return null;
      final String fqName = VfsUtilCore.getRelativePath(virtualFile, contentRoot, '/');
      if (fqName != null) return new FilePatternPackageSet(getModulePattern(node), fqName);
    }
    return null;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }

  private static final class CompactEmptyMiddlePackagesAction extends ToggleAction {
    private final Runnable myUpdate;

    CompactEmptyMiddlePackagesAction(Runnable update) {
      super(IdeBundle.message("action.compact.empty.middle.packages"),
            IdeBundle.message("action.compact.empty.middle.packages"), AllIcons.ObjectBrowser.CompactEmptyPackages);
      myUpdate = update;
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES = flag;
      myUpdate.run();
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(DependencyUISettings.getInstance().SCOPE_TYPE == FILE);
    }
  }
}
