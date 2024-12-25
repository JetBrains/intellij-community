// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;

public final class ModuleCompilerUtil {
  private ModuleCompilerUtil() { }

  public static Module @NotNull [] getDependencies(Module module) {
    return ModuleRootManager.getInstance(module).getDependencies();
  }

  public static @Unmodifiable @NotNull List<Chunk<ModuleSourceSet>> getCyclicDependencies(@NotNull Project project, @NotNull List<? extends Module> modules) {
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

  private static @NotNull Graph<ModuleSourceSet> createModuleSourceDependenciesGraph(@NotNull RootModelProvider provider) {
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<>() {
      @Override
      public @NotNull Collection<ModuleSourceSet> getNodes() {
        Module[] modules = provider.getModules();
        List<ModuleSourceSet> result = new ArrayList<>(modules.length * 2);
        for (Module module : modules) {
          result.add(new ModuleSourceSet(module, ModuleSourceSet.Type.PRODUCTION));
          result.add(new ModuleSourceSet(module, ModuleSourceSet.Type.TEST));
        }
        return result;
      }

      @Override
      public @NotNull Iterator<ModuleSourceSet> getIn(final ModuleSourceSet n) {
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

  public static @Unmodifiable @NotNull List<Chunk<ModuleSourceSet>> computeSourceSetCycles(@NotNull ModulesProvider provider) {
    Graph<ModuleSourceSet> graph = createModuleSourceDependenciesGraph(provider);
    Collection<Chunk<ModuleSourceSet>> chunks = GraphAlgorithms.getInstance().computeStronglyConnectedComponents(graph);
    return removeSingleElementChunks(removeDummyNodes(filterDuplicates(removeSingleElementChunks(chunks)), provider));
  }

  private static List<Chunk<ModuleSourceSet>> removeDummyNodes(List<? extends Chunk<ModuleSourceSet>> chunks, ModulesProvider modulesProvider) {
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
    JavaSourceRootType type = set.getType().isTest()? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    ModuleRootModel rootModel = modulesProvider.getRootModel(set.getModule());
    for (ContentEntry entry : rootModel.getContentEntries()) {
      if (!entry.getSourceFolders(type).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static @Unmodifiable List<Chunk<ModuleSourceSet>> removeSingleElementChunks(Collection<? extends Chunk<ModuleSourceSet>> chunks) {
    return ContainerUtil.filter(chunks, chunk -> chunk.getNodes().size() > 1);
  }

  /**
   * Remove cycles in tests included in cycles between production parts
   */
  private static @Unmodifiable @NotNull List<Chunk<ModuleSourceSet>> filterDuplicates(@NotNull Collection<? extends Chunk<ModuleSourceSet>> sourceSetCycles) {
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

  private static @Nullable ModuleSourceSet.Type getCommonType(@NotNull Chunk<? extends ModuleSourceSet> cycle) {
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
