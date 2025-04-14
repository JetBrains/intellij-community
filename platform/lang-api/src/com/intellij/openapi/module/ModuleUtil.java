// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;

public final class ModuleUtil extends ModuleUtilCore {
  private static final ParameterizedCachedValueProvider<MultiMap<ModuleType<?>, Module>, Project> MODULE_BY_TYPE_VALUE_PROVIDER = param -> {
    MultiMap<ModuleType<?>, Module> map = new MultiMap<>();
    for (Module module : ModuleManager.getInstance(param).getModules()) {
      map.putValue(ModuleType.get(module), module);
    }
    return CachedValueProvider.Result.createSingleDependency(map, ProjectRootManager.getInstance(param));
  };

  private static final ParameterizedCachedValueProvider<Boolean, Project> HAS_TEST_ROOTS_PROVIDER = project -> {
    boolean hasTestRoots =
      Arrays.stream(ModuleManager.getInstance(project).getModules())
        .flatMap(module -> Arrays.stream(ModuleRootManager.getInstance(module).getContentEntries()))
        .flatMap(entry -> Arrays.stream(entry.getSourceFolders()))
        .anyMatch(folder -> folder.getRootType().isForTests());
    return CachedValueProvider.Result.createSingleDependency(hasTestRoots, ProjectRootManager.getInstance(project));
  };

  private static final Key<ParameterizedCachedValue<MultiMap<ModuleType<?>, Module>, Project>> MODULES_BY_TYPE_KEY = Key.create("MODULES_BY_TYPE");
  private static final Key<ParameterizedCachedValue<Boolean, Project>> HAS_TEST_ROOTS_KEY = Key.create("HAS_TEST_ROOTS");

  private ModuleUtil() {}

  public static @NotNull @Unmodifiable Collection<Module> getModulesOfType(@NotNull Project project, @NotNull ModuleType<?> moduleType) {
    return CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(project, MODULES_BY_TYPE_KEY, MODULE_BY_TYPE_VALUE_PROVIDER, false, project)
      .get(moduleType);
  }

  public static boolean hasModulesOfType(@NotNull Project project, @NotNull ModuleType<?> module) {
    return !getModulesOfType(project, module).isEmpty();
  }

  public static boolean hasTestSourceRoots(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(project, HAS_TEST_ROOTS_KEY, HAS_TEST_ROOTS_PROVIDER, false, project);
  }

  /** @deprecated use {@link ModuleType#get(Module)} instead */
  @Deprecated(forRemoval = true)
  public static ModuleType<?> getModuleType(@NotNull Module module) {
    return ModuleType.get(module);
  }
}