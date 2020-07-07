// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  public static Collection<Module> getModulesOfType(@NotNull Project project, @NotNull ModuleType<?> moduleType) {
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

  /**
   * Returns some directory which is located near module files. <br>
   * There is no such thing as "base directory" for a module in IntelliJ project model. A module may have multiple content roots, or not have
   * content roots at all. The module configuration file (.iml) may be located far away from the module files or doesn't exist at all. So this
   * method tries to suggest some directory which is related to the module but due to its heuristics nature its result shouldn't be used for
   * real actions as is, user should be able to review and change it. For example it can be used as a default selection in a file chooser.
   */
  public static @Nullable VirtualFile suggestBaseDirectory(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length > 0) return contentRoots[0];
    VirtualFile moduleFile = module.getModuleFile();
    return moduleFile != null ? moduleFile.getParent() : null;
  }

  /** @deprecated use {@link ModuleType#get(Module)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static ModuleType<?> getModuleType(@NotNull Module module) {
    return ModuleType.get(module);
  }
}