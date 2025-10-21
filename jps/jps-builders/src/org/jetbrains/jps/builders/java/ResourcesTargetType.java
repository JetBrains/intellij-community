// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;

public final class ResourcesTargetType extends ModuleBasedBuildTargetType<ResourcesTarget> {
  public static final ResourcesTargetType PRODUCTION = new ResourcesTargetType("resources-production", false);
  public static final ResourcesTargetType TEST = new ResourcesTargetType("resources-test", true);
  public static final List<ResourcesTargetType> ALL_TYPES = List.of(PRODUCTION, TEST);

  private final boolean myTests;

  private ResourcesTargetType(String typeId, boolean tests) {
    super(typeId, true);
    myTests = tests;
  }

  @Override
  public @NotNull List<ResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JpsModule> modules = model.getProject().getModules();
    List<ResourcesTarget> targets = new ArrayList<>(modules.size());
    for (JpsModule module : modules) {
      targets.add(new ResourcesTarget(module, this));
    }
    return targets;
  }

  @Override
  public @NotNull BuildTargetLoader<ResourcesTarget> createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  public boolean isTests() {
    return myTests;
  }

  public static ResourcesTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }

  private final class Loader extends BuildTargetLoader<ResourcesTarget> {
    private final @NotNull JpsProject myProject;

    Loader(JpsModel model) {
      myProject = model.getProject();
    }

    @Override
    public @Nullable ResourcesTarget createTarget(@NotNull String targetId) {
      JpsModule module = myProject.findModuleByName(targetId);
      return module != null ? new ResourcesTarget(module, ResourcesTargetType.this) : null;
    }
  }
}
