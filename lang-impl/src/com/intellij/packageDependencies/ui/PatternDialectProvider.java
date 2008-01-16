/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PatternDialectProvider {
  public static final ExtensionPointName<PatternDialectProvider> EP_NAME = ExtensionPointName.create("com.intellij.patternDialectProvider");

  public static PatternDialectProvider getInstance(String shortName) {
    for (PatternDialectProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (Comparing.strEqual(provider.getShortName(), shortName)) return provider;
    }
    return null; //todo replace with File
  }

  public abstract TreeModel createTreeModel(Project project, Marker marker);

  public abstract String getDisplayName();

  @NonNls @NotNull
  public abstract String getShortName();

  public abstract AnAction[] createActions(final Runnable update);

  @Nullable
  public abstract PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively);

  @Nullable
  protected static String getModulePattern(final PackageDependenciesNode node) {
    final ModuleNode moduleParent = getModuleParent(node);
    return moduleParent != null ? moduleParent.getModuleName() : null;
  }

  @Nullable
  protected static ModuleNode getModuleParent(PackageDependenciesNode node) {
    if (node instanceof ModuleNode) return (ModuleNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getModuleParent((PackageDependenciesNode)node.getParent());
  }
}
