/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class BuildTargetIndexImpl implements BuildTargetIndex {
  private final BuildTargetRegistry myRegistry;
  private final BuildRootIndexImpl myBuildRootIndex;
  private final Map<BuildTarget<?>, Collection<BuildTarget<?>>> myDependencies;
  private List<BuildTargetChunk> myTargetChunks;

  public BuildTargetIndexImpl(BuildTargetRegistry targetRegistry, BuildRootIndexImpl buildRootIndex) {
    myRegistry = targetRegistry;
    myBuildRootIndex = buildRootIndex;
    myDependencies = new THashMap<>();
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
    Map<BuildTarget<?>, Collection<BuildTarget<?>>> dummyTargetDependencies = new HashMap<>();
    final List<BuildTarget<?>> realTargets = new ArrayList<>(allTargets.size());
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

    Graph<BuildTarget<?>> graph = GraphGenerator.generate(new InboundSemiGraph<BuildTarget<?>>() {
      @Override
      public Collection<BuildTarget<?>> getNodes() {
        return allTargets;
      }

      @Override
      public Iterator<BuildTarget<?>> getIn(BuildTarget<?> n) {
        Collection<BuildTarget<?>> deps = myDependencies.get(n);
        return deps != null ? deps.iterator() : Collections.emptyIterator();
      }
    });

    DFSTBuilder<BuildTarget<?>> builder = new DFSTBuilder<>(graph);
    Collection<Collection<BuildTarget<?>>> components = builder.getComponents();
    myTargetChunks = new ArrayList<>(components.size());
    for (Collection<BuildTarget<?>> component : components) {
      myTargetChunks.add(new BuildTargetChunk(ContainerUtil.newLinkedHashSet(component)));
    }
  }

  private static Collection<BuildTarget<?>> includeTransitiveDependenciesOfDummyTargets(Collection<BuildTarget<?>> dependencies,
                                                                                        Map<BuildTarget<?>, Collection<BuildTarget<?>>> dummyTargetDependencies) {
    ArrayList<BuildTarget<?>> realDependencies = new ArrayList<>(dependencies.size());
    Set<BuildTarget<?>> processed = new HashSet<>(dependencies);
    Queue<BuildTarget<?>> toProcess = new ArrayDeque<>(dependencies);
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
      realDependencies.add(dep);
    }
    realDependencies.trimToSize();
    return realDependencies;
  }

  @Override
  public boolean isDummy(@NotNull BuildTarget<?> target) {
    return target.getTargetType().isFileBased() && myBuildRootIndex.getTargetRoots(target, null).isEmpty();
  }

  @Override
  public Set<BuildTarget<?>> getDependenciesRecursively(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    initializeChunks(context);
    LinkedHashSet<BuildTarget<?>> result = new LinkedHashSet<>();
    for (BuildTarget<?> dep : getDependencies(target, context)) {
      collectDependenciesRecursively(dep, result, context);
    }
    return result;
  }

  private void collectDependenciesRecursively(BuildTarget<?> target, LinkedHashSet<BuildTarget<?>> result, CompileContext context) {
    if (result.add(target)) {
      for (BuildTarget<?> dep : getDependencies(target,context)) {
        collectDependenciesRecursively(dep, result, context);
      }
    }
  }

  @NotNull
  @Override
  public Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    initializeChunks(context);
    Collection<BuildTarget<?>> deps = myDependencies.get(target);
    return deps != null ? deps : Collections.emptyList();
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
