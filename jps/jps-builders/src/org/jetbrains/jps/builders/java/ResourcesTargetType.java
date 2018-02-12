/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class ResourcesTargetType extends ModuleBasedBuildTargetType<ResourcesTarget> {
  public static final ResourcesTargetType PRODUCTION = new ResourcesTargetType("resources-production", false);
  public static final ResourcesTargetType TEST = new ResourcesTargetType("resources-test", true);
  public static final List<ResourcesTargetType> ALL_TYPES = Arrays.asList(PRODUCTION, TEST);

  private final boolean myTests;

  private ResourcesTargetType(String typeId, boolean tests) {
    super(typeId, true);
    myTests = tests;
  }

  @NotNull
  @Override
  public List<ResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JpsModule> modules = model.getProject().getModules();
    List<ResourcesTarget> targets = new ArrayList<>(modules.size());
    for (JpsModule module : modules) {
      targets.add(new ResourcesTarget(module, this));
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

  public static ResourcesTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }

  private class Loader extends BuildTargetLoader<ResourcesTarget> {
    private final Map<String, JpsModule> myModules;

    public Loader(JpsModel model) {
      myModules = new HashMap<>();
      for (JpsModule module : model.getProject().getModules()) {
        myModules.put(module.getName(), module);
      }
    }

    @Nullable
    @Override
    public ResourcesTarget createTarget(@NotNull String targetId) {
      JpsModule module = myModules.get(targetId);
      return module != null ? new ResourcesTarget(module, ResourcesTargetType.this) : null;
    }
  }
}
