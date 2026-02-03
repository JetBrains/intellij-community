// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;

public final class JavaModuleBuildTargetType extends ModuleBasedBuildTargetType<ModuleBuildTarget> {
  public static final JavaModuleBuildTargetType PRODUCTION = new JavaModuleBuildTargetType("java-production", false);
  public static final JavaModuleBuildTargetType TEST = new JavaModuleBuildTargetType("java-test", true);
  public static final List<JavaModuleBuildTargetType> ALL_TYPES = List.of(PRODUCTION, TEST);

  private final boolean isTest;

  private JavaModuleBuildTargetType(String typeId, boolean tests) {
    super(typeId, true);
    isTest = tests;
  }

  @Override
  public @NotNull List<ModuleBuildTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JpsModule> modules = model.getProject().getModules();
    List<ModuleBuildTarget> targets = new ArrayList<>(modules.size());
    for (JpsModule module : modules) {
      targets.add(new ModuleBuildTarget(module, this));
    }
    return targets;
  }

  @Override
  public @NotNull BuildTargetLoader<ModuleBuildTarget> createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  public boolean isTests() {
    return isTest;
  }

  public static JavaModuleBuildTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }

  private final class Loader extends BuildTargetLoader<ModuleBuildTarget> {
    private final @NotNull JpsProject myProject;

    Loader(JpsModel model) {
      myProject = model.getProject();
    }

    @Override
    public @Nullable ModuleBuildTarget createTarget(@NotNull String targetId) {
      JpsModule module = myProject.findModuleByName(targetId);
      return module == null ? null : new ModuleBuildTarget(module, JavaModuleBuildTargetType.this);
    }
  }
}
