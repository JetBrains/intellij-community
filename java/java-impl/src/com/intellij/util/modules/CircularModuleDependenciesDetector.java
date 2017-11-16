/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.modules;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Couple;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CircularModuleDependenciesDetector {
  @NotNull
  private static <T extends ModuleRootModel> Graph<T> createGraphGenerator(@NotNull Map<Module, T> models) {
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<T>() {
      @Override
      public Collection<T> getNodes() {
        return models.values();
      }

      @Override
      public Iterator<T> getIn(final ModuleRootModel model) {
        final List<T> dependencies = new ArrayList<>();
        model.orderEntries().compileOnly().forEachModule(module -> {
          T depModel = models.get(module);
          if (depModel != null) {
            dependencies.add(depModel);
          }
          return true;
        });
        return dependencies.iterator();
      }
    }));
  }

  @NotNull
  private static <T extends ModuleRootModel> Collection<Chunk<T>> buildChunks(@NotNull Map<Module, T> models) {
    return GraphAlgorithms.getInstance().computeSCCGraph(createGraphGenerator(models)).getNodes();
  }
  /**
   * @return pair of modules which become circular after adding dependency, or null if all remains OK
   */
  @Nullable
  public static Couple<Module> addingDependencyFormsCircularity(@NotNull Module currentModule, @NotNull Module toDependOn) {
    assert currentModule != toDependOn;
    // whatsa lotsa of @&#^%$ codes-a!

    final Map<Module, ModifiableRootModel> models = new LinkedHashMap<>();
    Project project = currentModule.getProject();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      models.put(module, model);
    }
    ModifiableRootModel currentModel = models.get(currentModule);
    ModifiableRootModel toDependOnModel = models.get(toDependOn);
    Collection<Chunk<ModifiableRootModel>> nodesBefore = buildChunks(models);
    for (Chunk<ModifiableRootModel> chunk : nodesBefore) {
      if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) return null; // they circular already
    }

    try {
      currentModel.addModuleOrderEntry(toDependOn);
      Collection<Chunk<ModifiableRootModel>> nodesAfter = buildChunks(models);
      for (Chunk<ModifiableRootModel> chunk : nodesAfter) {
        if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) {
          List<ModifiableRootModel> nodes = ContainerUtil.collect(chunk.getNodes().iterator());
          // graph algorithms collections are inherently unstable, so sort to return always the same modules to avoid blinking tests
          nodes.sort(Comparator.comparing(m -> m.getModule().getName()));
          return Couple.of(nodes.get(0).getModule(), nodes.get(1).getModule());
        }
      }
    }
    finally {
      for (ModifiableRootModel model : models.values()) {
        model.dispose();
      }
    }
    return null;
  }
}
