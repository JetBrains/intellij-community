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
package org.jetbrains.jps.builders.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class BuildTargetIndexImpl implements BuildTargetIndex {
  private final BuildTargetRegistry myRegistry;
  private final BuildRootIndexImpl myBuildRootIndex;
  private Map<BuildTarget<?>, Collection<BuildTarget<?>>> myDependencies;
  private List<BuildTargetChunk> myTargetChunks;

  public BuildTargetIndexImpl(BuildTargetRegistry targetRegistry, BuildRootIndexImpl buildRootIndex) {
    myRegistry = targetRegistry;
    myBuildRootIndex = buildRootIndex;
    myDependencies = new THashMap<BuildTarget<?>, Collection<BuildTarget<?>>>();
  }

  @Override
  public List<BuildTargetChunk> getSortedTargetChunks(@NotNull CompileContext context) {
    initializeChunks(context);
    return myTargetChunks;
  }


  private synchronized void initializeChunks(@NotNull CompileContext context) {
    if (myTargetChunks != null) {
      return;
    }

    List<BuildTarget<?>> allTargets = getAllTargets();
    TargetOutputIndex outputIndex = new TargetOutputIndexImpl(allTargets, context);
    Map<BuildTarget<?>, Collection<BuildTarget<?>>> dummyTargetDependencies = new HashMap<BuildTarget<?>, Collection<BuildTarget<?>>>();
    final List<BuildTarget<?>> realTargets = new ArrayList<BuildTarget<?>>(allTargets.size());
    for (BuildTarget<?> target : allTargets) {
      if (isDummy(target)) {
        dummyTargetDependencies.put(target, target.computeDependencies(myRegistry, outputIndex));
      }
      else {
        realTargets.add(target);
      }
    }

    for (BuildTarget<?> target : realTargets) {
      Collection<BuildTarget<?>> dependencies = target.computeDependencies(this, outputIndex);
      Collection<BuildTarget<?>> realDependencies;
      if (!ContainerUtil.intersects(dependencies, dummyTargetDependencies.keySet())) {
        realDependencies = dependencies;
      }
      else {
        realDependencies = includeTransitiveDependenciesOfDummyTargets(dependencies, dummyTargetDependencies);
      }
      myDependencies.put(target, realDependencies);
    }

    GraphGenerator<BuildTarget<?>>
      graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<BuildTarget<?>>() {
      @Override
      public Collection<BuildTarget<?>> getNodes() {
        return realTargets;
      }

      @Override
      public Iterator<BuildTarget<?>> getIn(BuildTarget<?> n) {
        return myDependencies.get(n).iterator();
      }
    }));

    final DFSTBuilder<BuildTarget<?>> builder = new DFSTBuilder<BuildTarget<?>>(graph);
    final TIntArrayList sccs = builder.getSCCs();

    myTargetChunks = new ArrayList<BuildTargetChunk>(sccs.size());
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        final Set<BuildTarget<?>> chunkNodes = new LinkedHashSet<BuildTarget<?>>();
        for (int j = 0; j < size; j++) {
          final BuildTarget<?> node = builder.getNodeByTNumber(myTNumber + j);
          chunkNodes.add(node);
        }
        myTargetChunks.add(new BuildTargetChunk(chunkNodes));

        myTNumber += size;
        return true;
      }
    });
  }

  private static Collection<BuildTarget<?>> includeTransitiveDependenciesOfDummyTargets(Collection<BuildTarget<?>> dependencies,
                                                                                        Map<BuildTarget<?>, Collection<BuildTarget<?>>> dummyTargetDependencies) {
    ArrayList<BuildTarget<?>> realDependencies = new ArrayList<BuildTarget<?>>(dependencies.size());
    Set<BuildTarget<?>> processed = new HashSet<BuildTarget<?>>(dependencies);
    Queue<BuildTarget<?>> toProcess = new ArrayDeque<BuildTarget<?>>(dependencies);
    while (!toProcess.isEmpty()) {
      BuildTarget<?> dep = toProcess.poll();
      Collection<BuildTarget<?>> toInclude = dummyTargetDependencies.get(dep);
      if (toInclude != null) {
        for (BuildTarget<?> target : toInclude) {
          if (processed.add(target)) {
            toProcess.add(target);
          }
        }
      }
      else {
        realDependencies.add(dep);
      }
    }
    realDependencies.trimToSize();
    return realDependencies;
  }

  @Override
  public boolean isDummy(@NotNull BuildTarget<?> target) {
    return (target instanceof ModuleBuildTarget || target instanceof ResourcesTarget) //todo[nik] introduce method in BuildTarget instead
         && myBuildRootIndex.getTargetRoots(target, null).isEmpty();
  }

  @Override
  public Set<BuildTarget<?>> getDependenciesRecursively(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    initializeChunks(context);
    LinkedHashSet<BuildTarget<?>> result = new LinkedHashSet<BuildTarget<?>>();
    for (BuildTarget<?> dep : myDependencies.get(target)) {
      collectDependenciesRecursively(dep, result);
    }
    return result;
  }

  private void collectDependenciesRecursively(BuildTarget<?> target, LinkedHashSet<BuildTarget<?>> result) {
    if (result.add(target)) {
      for (BuildTarget<?> dep : myDependencies.get(target)) {
        collectDependenciesRecursively(dep, result);
      }
    }
  }

  @NotNull
  @Override
  public Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    initializeChunks(context);
    return myDependencies.get(target);
  }

  @NotNull
  public Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@NotNull JpsModule module, @NotNull ModuleTargetSelector selector) {
    return myRegistry.getModuleBasedTargets(module, selector);
  }

  @NotNull
  public <T extends BuildTarget<?>> List<T> getAllTargets(@NotNull BuildTargetType<T> type) {
    return myRegistry.getAllTargets(type);
  }

  @NotNull
  public List<BuildTarget<?>> getAllTargets() {
    return myRegistry.getAllTargets();
  }
}
