/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdatingScopeOnProjectStructureChangeListener implements ProjectComponent, ModuleListener {
  public UpdatingScopeOnProjectStructureChangeListener(MessageBus messageBus) {
    messageBus.connect().subscribe(ProjectTopics.MODULES, this);
  }

  @Override
  public void modulesRenamed(@NotNull Project project,
                             @NotNull List<Module> modules,
                             @NotNull Function<Module, String> oldNameProvider) {
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
      return new NamedScope(scope.getName(), newSet);
    }
    return scope;
  }
}
