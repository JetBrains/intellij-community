// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class UpdatingScopeOnProjectStructureChangeListener implements ModuleListener {
  @Override
  public void modulesRenamed(@NotNull Project project,
                             @NotNull List<? extends Module> modules,
                             @NotNull Function<? super Module, String> oldNameProvider) {
    Map<String, String> moduleMap = modules.stream().collect(Collectors.toMap(oldNameProvider::fun, Module::getName));
    for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
      NamedScope[] oldScopes = holder.getEditableScopes();
      NamedScope[] newScopes = new NamedScope[oldScopes.length];
      for (int i = 0; i < oldScopes.length; i++) {
        newScopes[i] = renameModulesIn(oldScopes[i], moduleMap);
      }
      if (!Arrays.equals(newScopes, oldScopes)) {
        holder.setScopes(newScopes);
      }
    }
  }

  private static NamedScope renameModulesIn(NamedScope scope, Map<String, String> nameMapping) {
    PackageSet oldSet = scope.getValue();
    if (oldSet == null) return scope;

    PackageSet newSet = oldSet.map(packageSet -> {
      if (packageSet instanceof PatternBasedPackageSet) {
        String modulePattern = ((PatternBasedPackageSet)packageSet).getModulePattern();
        String newName = nameMapping.get(modulePattern);
        if (newName != null) {
          return ((PatternBasedPackageSet)packageSet).updateModulePattern(modulePattern, newName);
        }
      }
      return packageSet;
    });
    if (newSet != oldSet) {
      String presentableName = scope.getPresentableName();
      return new NamedScope(scope.getScopeId(), () -> presentableName, scope.getIcon(), newSet);
    }
    return scope;
  }
}
