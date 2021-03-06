// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JavaModuleBuildTargetType extends ModuleBasedBuildTargetType<ModuleBuildTarget> {
  public static final JavaModuleBuildTargetType PRODUCTION = new JavaModuleBuildTargetType("java-production", false);
  public static final JavaModuleBuildTargetType TEST = new JavaModuleBuildTargetType("java-test", true);
  public static final List<JavaModuleBuildTargetType> ALL_TYPES = ContainerUtil.immutableList(PRODUCTION, TEST);

  private final boolean myTests;

  private JavaModuleBuildTargetType(String typeId, boolean tests) {
    super(typeId, true);
    myTests = tests;
  }

  @NotNull
  @Override
  public List<ModuleBuildTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JpsModule> modules = model.getProject().getModules();
    List<ModuleBuildTarget> targets = new ArrayList<>(modules.size());
    for (JpsModule module : modules) {
      targets.add(new ModuleBuildTarget(module, this));
    }
    return targets;
  }

  @NotNull
  @Override
  public Loader createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  public boolean isTests() {
    return myTests;
  }

  public static JavaModuleBuildTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }

  private class Loader extends BuildTargetLoader<ModuleBuildTarget> {
    private final Map<String, JpsModule> myModules;

    Loader(JpsModel model) {
      myModules = new HashMap<>();
      for (JpsModule module : model.getProject().getModules()) {
        myModules.put(module.getName(), module);
      }
    }

    @Nullable
    @Override
    public ModuleBuildTarget createTarget(@NotNull String targetId) {
      JpsModule module = myModules.get(targetId);
      return module != null ? new ModuleBuildTarget(module, JavaModuleBuildTargetType.this) : null;
    }
  }
}
