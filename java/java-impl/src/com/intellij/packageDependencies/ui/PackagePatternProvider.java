// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.GroupByScopeTypeAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class PackagePatternProvider extends PatternDialectProvider {
  @NonNls public static final String PACKAGES = "package";
  private static final Logger LOG = Logger.getInstance(PackagePatternProvider.class);

  @Nullable
  private static GeneralGroupNode getGroupParent(PackageDependenciesNode node) {
    if (node instanceof GeneralGroupNode) return (GeneralGroupNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getGroupParent((PackageDependenciesNode)node.getParent());
  }

  public PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    GeneralGroupNode groupParent = getGroupParent(node);
    String scope1 = PatternPackageSet.SCOPE_ANY;
    if (groupParent != null) {
      String name = groupParent.toString();
      if (TreeModelBuilder.PRODUCTION_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_SOURCE;
      }
      else if (TreeModelBuilder.TEST_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_TEST;
      }
      else if (TreeModelBuilder.LIBRARY_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_LIBRARY;
      }
    }
    final String scope = scope1;
    if (node instanceof ModuleGroupNode){
      if (!recursively) return null;
      return new PatternPackageSet("*..*", scope, ProjectPatternProvider.getGroupModulePattern((ModuleGroupNode)node));
    } else if (node instanceof ModuleNode) {
      if (!recursively) return null;
      final String modulePattern = ((ModuleNode)node).getModuleName();
      return new PatternPackageSet("*..*", scope, modulePattern);
    }
    else if (node instanceof PackageNode) {
      String pattern = ((PackageNode)node).getPackageQName();
      if (pattern != null) {
        pattern += recursively ? "..*" : ".*";
      }
      else {
        pattern = recursively ? "*..*" : "*";
      }

      return new PatternPackageSet(pattern, scope, getModulePattern(node));
    }
    else if (node instanceof FileNode) {
      if (recursively) return null;
      FileNode fNode = (FileNode)node;
      final PsiElement element = fNode.getPsiElement();
      String qName = null;
      if (element instanceof PsiClassOwner) {
        final PsiClassOwner javaFile = (PsiClassOwner)element;
        final VirtualFile virtualFile = javaFile.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        final String packageName =
          ProjectRootManager.getInstance(element.getProject()).getFileIndex().getPackageNameByDirectory(virtualFile.getParent());
        final String name = virtualFile.getNameWithoutExtension();
        if (!PsiNameHelper.getInstance(element.getProject()).isIdentifier(name)) return null;
        qName = StringUtil.getQualifiedName(packageName, name);
      }
      if (qName != null) {
        return new PatternPackageSet(qName, scope, getModulePattern(node));
      }
    }
    else if (node instanceof GeneralGroupNode) {
      return new PatternPackageSet("*..*", scope, null);
    }

    return null;
  }

  public Icon getIcon() {
    return AllIcons.Nodes.CopyOfFolder;
  }

  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return TreeModelBuilder.createTreeModel(project, false, marker);
  }

  public TreeModel createTreeModel(final Project project, final Set<PsiFile> deps, final Marker marker,
                                   final DependenciesPanel.DependencyPanelSettings settings) {
    return TreeModelBuilder.createTreeModel(project, false, deps, marker, settings);
  }

  public String getDisplayName() {
    return IdeBundle.message("title.packages");
  }

  @NotNull
  public String getShortName() {
    return PACKAGES;
  }

  public AnAction[] createActions(Project project, final Runnable update) {
    return new AnAction[]{new GroupByScopeTypeAction(update)};
  }
}
