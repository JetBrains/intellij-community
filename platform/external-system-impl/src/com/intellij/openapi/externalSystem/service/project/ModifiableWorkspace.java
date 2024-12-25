// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public final class ModifiableWorkspace {
  private final Map<ProjectCoordinate, String> myModuleMappingById = CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
    @Override
    public int hashCode(ProjectCoordinate object) {
      if (object == null) {
        return 0;
      }

      String groupId = object.getGroupId();
      String artifactId = object.getArtifactId();
      String version = object.getVersion();
      int result1 = (groupId != null ? groupId.hashCode() : 0);
      result1 = 31 * result1 + (artifactId != null ? artifactId.hashCode() : 0);
      result1 = 31 * result1 + (version != null ? version.hashCode() : 0);
      return result1;
    }

    @Override
    public boolean equals(ProjectCoordinate o1, ProjectCoordinate o2) {
      if (o1 == o2) {
        return true;
      }
      if (o1 == null || o2 == null) {
        return false;
      }

      if (o1.getGroupId() != null ? !o1.getGroupId().equals(o2.getGroupId()) : o2.getGroupId() != null) return false;
      if (o1.getArtifactId() != null ? !o1.getArtifactId().equals(o2.getArtifactId()) : o2.getArtifactId() != null) return false;
      if (o1.getVersion() != null ? !o1.getVersion().equals(o2.getVersion()) : o2.getVersion() != null) return false;
      return true;
    }
  });

  private final ExternalProjectsWorkspaceImpl.State myState;
  private final MultiMap<String/* module owner */, String /* substitution modules */> mySubstitutions = MultiMap.createSet();
  private final Map<String /* module name */, String /* library name */> myNamesMap = new HashMap<>();
  private final Supplier<? extends List<Module>> myModulesSupplier;


  ModifiableWorkspace(ExternalProjectsWorkspaceImpl.State state,
                             Supplier<? extends List<Module>> modulesSupplier) {
    myModulesSupplier = modulesSupplier;
    Set<String> existingModules = new HashSet<>();
    for (Module module : modulesSupplier.get()) {
      register(module);
      existingModules.add(module.getName());
    }
    myState = state;
    if (myState.names != null) {
      for (Map.Entry<String, String> entry : myState.names.entrySet()) {
        if (existingModules.contains(entry.getKey())) {
          myNamesMap.put(entry.getKey(), entry.getValue());
        }
      }
    }

    if (myState.substitutions != null) {
      for (Map.Entry<String, Set<String>> entry : myState.substitutions.entrySet()) {
        if (existingModules.contains(entry.getKey())) {
          mySubstitutions.put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  public void commit() {
    Set<String> existingModules = new HashSet<>();
    myModulesSupplier.get().stream().map(Module::getName).forEach(existingModules::add);

    myState.names = new HashMap<>();
    myNamesMap.forEach((module, lib) -> {
      if (existingModules.contains(module)) {
        myState.names.put(module, lib);
      }
    });

    myState.substitutions = new HashMap<>();
    for (Map.Entry<String, Collection<String>> entry : mySubstitutions.entrySet()) {
      if (!existingModules.contains(entry.getKey())) continue;
      Collection<String> value = entry.getValue();
      if (value != null && !value.isEmpty()) {
        myState.substitutions.put(entry.getKey(), new TreeSet<>(value));
      }
    }
  }

  public void addSubstitution(String ownerModuleName,
                              String moduleName,
                              String libraryName,
                              DependencyScope scope) {
    myNamesMap.put(moduleName, libraryName);
    mySubstitutions.putValue(ownerModuleName, moduleName + '_' + scope.getDisplayName());
  }

  public void removeSubstitution(String ownerModuleName,
                                 String moduleName,
                                 String libraryName,
                                 DependencyScope scope) {
    mySubstitutions.remove(ownerModuleName, moduleName + '_' + scope.getDisplayName());
    Collection<String> substitutions = mySubstitutions.values();
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (substitutions.contains(moduleName + '_' + dependencyScope.getDisplayName())) {
        return;
      }
    }
    myNamesMap.remove(moduleName, libraryName);
  }

  public boolean isSubstitution(String moduleOwner, String substitutionModule, DependencyScope scope) {
    return mySubstitutions.get(moduleOwner).contains(substitutionModule + '_' + scope.getDisplayName());
  }

  public boolean isSubstituted(String libraryName) {
    return myNamesMap.containsValue(libraryName);
  }

  public String getSubstitutedLibrary(String moduleName) {
    return myNamesMap.get(moduleName);
  }

  public @Nullable String findModule(@NotNull ProjectCoordinate id) {
    if (StringUtil.isEmpty(id.getArtifactId())) return null;
    String result = myModuleMappingById.get(id);
    return result == null && id.getVersion() != null
           ? myModuleMappingById.get(new ProjectId(id.getGroupId(), id.getArtifactId(), null))
           : result;
  }

  public void register(@NotNull ProjectCoordinate id, @NotNull Module module) {
    myModuleMappingById.put(id, module.getName());
    myModuleMappingById.put(new ProjectId(id.getGroupId(), id.getArtifactId(), null), module.getName());
  }

  private void register(@NotNull Module module) {
    Arrays.stream(ExternalProjectsWorkspaceImpl.EP_NAME.getExtensions())
      .map(contributor -> contributor.findProjectId(module))
      .filter(Objects::nonNull)
      .findFirst()
      .ifPresent(id -> register(id, module));
  }
}
