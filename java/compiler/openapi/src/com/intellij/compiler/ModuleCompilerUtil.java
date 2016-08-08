/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.util.Chunk;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

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

    final Set<Module> modulesSet = new HashSet<>(modules);
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
    final List<Chunk<Node>> chunks = new ArrayList<>(chunkGraph.getNodes().size());
    for (final Chunk<Node> chunk : chunkGraph.getNodes()) {
      chunks.add(chunk);
    }
    DFSTBuilder<Chunk<Node>> builder = new DFSTBuilder<>(chunkGraph);
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
    Runnable sort = () -> {
      Comparator<Module> comparator = ModuleManager.getInstance(project).moduleDependencyComparator();
      Collections.sort(modules, comparator);
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

  /**
   * @return pair of modules which become circular after adding dependency, or null if all remains OK
   */
  @Nullable
  public static Couple<Module> addingDependencyFormsCircularity(final Module currentModule, Module toDependOn) {
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
          Iterator<ModifiableRootModel> nodes = chunk.getNodes().iterator();
          return Couple.of(nodes.next().getModule(), nodes.next().getModule());
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

  public static List<Chunk<ModuleSourceSet>> getCyclicDependencies(@NotNull Project project, @NotNull List<Module> modules) {
    Collection<Chunk<ModuleSourceSet>> chunks = computeSourceSetCycles(new DefaultModulesProvider(project));
    final Set<Module> modulesSet = new HashSet<>(modules);
    return ContainerUtil.filter(chunks, chunk -> {
      for (ModuleSourceSet sourceSet : chunk.getNodes()) {
        if (modulesSet.contains(sourceSet.getModule())) {
          return true;
        }
      }
      return false;
    });
  }

  private static Graph<ModuleSourceSet> createModuleSourceDependenciesGraph(final RootModelProvider provider) {
    return GraphGenerator.create(new CachingSemiGraph<>(new GraphGenerator.SemiGraph<ModuleSourceSet>() {
      @Override
      public Collection<ModuleSourceSet> getNodes() {
        Module[] modules = provider.getModules();
        List<ModuleSourceSet> result = new ArrayList<>(modules.length * 2);
        for (Module module : modules) {
          result.add(new ModuleSourceSet(module, ModuleSourceSet.Type.PRODUCTION));
          result.add(new ModuleSourceSet(module, ModuleSourceSet.Type.TEST));
        }
        return result;
      }

      @Override
      public Iterator<ModuleSourceSet> getIn(final ModuleSourceSet n) {
        ModuleRootModel model = provider.getRootModel(n.getModule());
        OrderEnumerator enumerator = model.orderEntries().compileOnly();
        if (n.getType() == ModuleSourceSet.Type.PRODUCTION) {
          enumerator = enumerator.productionOnly();
        }
        final List<ModuleSourceSet> deps = new ArrayList<>();
        enumerator.forEachModule(module -> {
          deps.add(new ModuleSourceSet(module, n.getType()));
          return true;
        });
        if (n.getType() == ModuleSourceSet.Type.TEST) {
          deps.add(new ModuleSourceSet(n.getModule(), ModuleSourceSet.Type.PRODUCTION));
        }
        return deps.iterator();
      }
    }));
  }

  @NotNull
  public static List<Chunk<ModuleSourceSet>> computeSourceSetCycles(@NotNull ModulesProvider provider) {
    Graph<ModuleSourceSet> graph = createModuleSourceDependenciesGraph(provider);
    Collection<Chunk<ModuleSourceSet>> chunks = GraphAlgorithms.getInstance().computeStronglyConnectedComponents(graph);
    return removeSingleElementChunks(removeDummyNodes(filterDuplicates(removeSingleElementChunks(chunks)), provider));
  }

  private static List<Chunk<ModuleSourceSet>> removeDummyNodes(List<Chunk<ModuleSourceSet>> chunks, ModulesProvider modulesProvider) {
    List<Chunk<ModuleSourceSet>> result = new ArrayList<>(chunks.size());
    for (Chunk<ModuleSourceSet> chunk : chunks) {
      Set<ModuleSourceSet> nodes = new LinkedHashSet<>();
      for (ModuleSourceSet sourceSet : chunk.getNodes()) {
        if (!isDummy(sourceSet, modulesProvider)) {
          nodes.add(sourceSet);
        }
      }
      result.add(new Chunk<>(nodes));
    }
    return result;
  }

  private static boolean isDummy(ModuleSourceSet set, ModulesProvider modulesProvider) {
    JavaSourceRootType type = set.getType() == ModuleSourceSet.Type.PRODUCTION ? JavaSourceRootType.SOURCE : JavaSourceRootType.TEST_SOURCE;
    ModuleRootModel rootModel = modulesProvider.getRootModel(set.getModule());
    for (ContentEntry entry : rootModel.getContentEntries()) {
      if (!entry.getSourceFolders(type).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static List<Chunk<ModuleSourceSet>> removeSingleElementChunks(Collection<Chunk<ModuleSourceSet>> chunks) {
    return ContainerUtil.filter(chunks, chunk -> chunk.getNodes().size() > 1);
  }

  /**
   * Remove cycles in tests included in cycles between production parts
   */
  @NotNull
  private static List<Chunk<ModuleSourceSet>> filterDuplicates(@NotNull Collection<Chunk<ModuleSourceSet>> sourceSetCycles) {
    final List<Set<Module>> productionCycles = new ArrayList<>();

    for (Chunk<ModuleSourceSet> cycle : sourceSetCycles) {
      ModuleSourceSet.Type type = getCommonType(cycle);
      if (type == ModuleSourceSet.Type.PRODUCTION) {
        productionCycles.add(ModuleSourceSet.getModules(cycle.getNodes()));
      }
    }

    return ContainerUtil.filter(sourceSetCycles, chunk -> {
      if (getCommonType(chunk) != ModuleSourceSet.Type.TEST) return true;
      for (Set<Module> productionCycle : productionCycles) {
        if (productionCycle.containsAll(ModuleSourceSet.getModules(chunk.getNodes()))) return false;
      }
      return true;
    });
  }

  @Nullable
  private static ModuleSourceSet.Type getCommonType(@NotNull Chunk<ModuleSourceSet> cycle) {
    ModuleSourceSet.Type type = null;
    for (ModuleSourceSet set : cycle.getNodes()) {
      if (type == null) {
        type = set.getType();
      }
      else if (type != set.getType()) {
        return null;
      }
    }
    return type;
  }
}
