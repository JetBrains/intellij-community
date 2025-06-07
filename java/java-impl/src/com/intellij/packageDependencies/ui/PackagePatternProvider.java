// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.scopeChooser.GroupByScopeTypeAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public final class PackagePatternProvider extends PatternDialectProvider {
  public static final @NonNls String PACKAGES = "package";
  private static final Logger LOG = Logger.getInstance(PackagePatternProvider.class);

  private static @Nullable GeneralGroupNode getGroupParent(PackageDependenciesNode node) {
    if (node instanceof GeneralGroupNode) return (GeneralGroupNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getGroupParent((PackageDependenciesNode)node.getParent());
  }

  @Override
  public PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    GeneralGroupNode groupParent = getGroupParent(node);
    PatternPackageSet.Scope scope = PatternPackageSet.Scope.ANY;
    if (groupParent != null) {
      String name = groupParent.toString();
      if (TreeModelBuilder.getProductionName().equals(name)) {
        scope = PatternPackageSet.Scope.SOURCE;
      }
      else if (TreeModelBuilder.getTestName().equals(name)) {
        scope = PatternPackageSet.Scope.TEST;
      }
      else if (TreeModelBuilder.getLibraryName().equals(name)) {
        scope = PatternPackageSet.Scope.LIBRARY;
      }
    }
    if (node instanceof ModuleGroupNode){
      if (!recursively) return null;
      return new PatternPackageSet("*..*", scope, PatternDialectProvider.getGroupModulePattern((ModuleGroupNode)node));
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
    else if (node instanceof FileNode fNode) {
      if (recursively) return null;
      final PsiElement element = fNode.getPsiElement();
      String qName = null;
      if (element instanceof PsiClassOwner javaFile) {
        final VirtualFile virtualFile = javaFile.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        final String packageName = PackageIndex.getInstance(element.getProject()).getPackageName(virtualFile);
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

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.CopyOfFolder;
  }

  @Override
  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return TreeModelBuilder.createTreeModel(project, false, marker);
  }

  @Override
  public TreeModel createTreeModel(final Project project, final Set<? extends PsiFile> deps, final Marker marker,
                                   final DependenciesPanel.DependencyPanelSettings settings) {
    return TreeModelBuilder.createTreeModel(project, false, deps, marker, settings);
  }

  @Override
  public String getDisplayName() {
    return JavaBundle.message("title.packages");
  }

  @Override
  public @NotNull String getShortName() {
    return PACKAGES;
  }

  @Override
  public @Nls @NotNull String getHintMessage() {
    return JavaBundle.message("package.pattern.provider.hint.label");
  }

  @Override
  public AnAction[] createActions(Project project, final Runnable update) {
    return new AnAction[]{new GroupByScopeTypeAction(update)};
  }
}
