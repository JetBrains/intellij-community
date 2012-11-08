/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.compiler;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public final class ModuleCompilerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.ModuleCompilerUtil");
  private ModuleCompilerUtil() { }

  public static Module[] getDependencies(Module module) {
    return ModuleRootManager.getInstance(module).getDependencies();
  }

  public static Graph<Module> createModuleGraph(final Module[] modules) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
      public Collection<Module> getNodes() {
        return Arrays.asList(modules);
      }

      public Iterator<Module> getIn(Module module) {
        return Arrays.asList(getDependencies(module)).iterator();
      }
    }));
  }

  public static List<Chunk<Module>> getSortedModuleChunks(Project project, List<Module> modules) {
    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    final List<Chunk<Module>> chunks = getSortedChunks(createModuleGraph(allModules));

    final Set<Module> modulesSet = new HashSet<Module>(modules);
    // leave only those chunks that contain at least one module from modules
    for (Iterator<Chunk<Module>> it = chunks.iterator(); it.hasNext();) {
      final Chunk<Module> chunk = it.next();
      if (!ContainerUtil.intersects(chunk.getNodes(), modulesSet)) {
        it.remove();
      }
    }
    return chunks;
  }

  public static <Node> List<Chunk<Node>> getSortedChunks(final Graph<Node> graph) {
    final Graph<Chunk<Node>> chunkGraph = toChunkGraph(graph);
    final List<Chunk<Node>> chunks = new ArrayList<Chunk<Node>>(chunkGraph.getNodes().size());
    for (final Chunk<Node> chunk : chunkGraph.getNodes()) {
      chunks.add(chunk);
    }
    DFSTBuilder<Chunk<Node>> builder = new DFSTBuilder<Chunk<Node>>(chunkGraph);
    if (!builder.isAcyclic()) {
      LOG.error("Acyclic graph expected");
      return null;
    }

    Collections.sort(chunks, builder.comparator());
    return chunks;
  }
  
  public static <Node> Graph<Chunk<Node>> toChunkGraph(final Graph<Node> graph) {
    return GraphAlgorithms.getInstance().computeSCCGraph(graph);
  }

  public static void sortModules(final Project project, final List<Module> modules) {
    final Application application = ApplicationManager.getApplication();
    Runnable sort = new Runnable() {
      public void run() {
        Comparator<Module> comparator = ModuleManager.getInstance(project).moduleDependencyComparator();
        Collections.sort(modules, comparator);
      }
    };
    if (application.isDispatchThread()) {
      sort.run();
    }
    else {
      application.runReadAction(sort);
    }
  }


  public static <T extends ModuleRootModel> GraphGenerator<T> createGraphGenerator(final Map<Module, T> models) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<T>() {
      public Collection<T> getNodes() {
        return models.values();
      }

      public Iterator<T> getIn(final ModuleRootModel model) {
        final Module[] modules = model.getModuleDependencies();
        final List<T> dependencies = new ArrayList<T>();
        for (Module module : modules) {
          T depModel = models.get(module);
          if (depModel != null) {
            dependencies.add(depModel);
          }
        }
        return dependencies.iterator();
      }
    }));
  }

  /**
   * @return pair of modules which become circular after adding dependency, or null if all remains OK
   */
  @Nullable
  public static Pair<Module, Module> addingDependencyFormsCircularity(final Module currentModule, Module toDependOn) {
    assert currentModule != toDependOn;
    // whatsa lotsa of @&#^%$ codes-a!

    final Map<Module, ModifiableRootModel> models = new LinkedHashMap<Module, ModifiableRootModel>();
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
          Iterator<ModifiableRootModel> nodes = chunk.getNodes().iterator();
          return Pair.create(nodes.next().getModule(), nodes.next().getModule());
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

  public static <T extends ModuleRootModel> Collection<Chunk<T>> buildChunks(final Map<Module, T> models) {
    return toChunkGraph(createGraphGenerator(models)).getNodes();
  }
}
