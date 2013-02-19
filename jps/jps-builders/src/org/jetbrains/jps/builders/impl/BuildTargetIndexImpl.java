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

import com.intellij.util.SmartList;
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
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class BuildTargetIndexImpl implements BuildTargetIndex {
  private Map<BuildTargetType<?>, List<? extends BuildTarget<?>>> myTargets;
  private Map<JpsModule, List<ModuleBasedTarget>> myModuleBasedTargets;
  private Map<BuildTarget<?>, Collection<BuildTarget<?>>> myDependencies;
  private List<BuildTargetChunk> myTargetChunks;
  private final List<BuildTarget<?>> myAllTargets;

  public BuildTargetIndexImpl(@NotNull JpsModel model) {
    myTargets = new THashMap<BuildTargetType<?>, List<? extends BuildTarget<?>>>();
    myModuleBasedTargets = new THashMap<JpsModule, List<ModuleBasedTarget>>();
    List<List<? extends BuildTarget<?>>> targetsByType = new ArrayList<List<? extends BuildTarget<?>>>();
    for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
      List<? extends BuildTarget<?>> targets = type.computeAllTargets(model);
      myTargets.put(type, targets);
      targetsByType.add(targets);
      for (BuildTarget<?> target : targets) {
        if (target instanceof ModuleBasedTarget) {
          final ModuleBasedTarget t = (ModuleBasedTarget)target;
          final JpsModule module = t.getModule();
          List<ModuleBasedTarget> list = myModuleBasedTargets.get(module);
          if (list == null) {
            list = new ArrayList<ModuleBasedTarget>();
            myModuleBasedTargets.put(module, list);
          }
          list.add(t);
        }
      }
    }
    myDependencies = new THashMap<BuildTarget<?>, Collection<BuildTarget<?>>>();
    myAllTargets = Collections.unmodifiableList(ContainerUtil.concat(targetsByType));
  }

  @Override
  public Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@NotNull JpsModule module, @NotNull ModuleTargetSelector selector) {
    final List<ModuleBasedTarget> targets = myModuleBasedTargets.get(module);
    if (targets == null || targets.isEmpty()) {
      return Collections.emptyList();
    }
    final List<ModuleBasedTarget<?>> result = new SmartList<ModuleBasedTarget<?>>();
    for (ModuleBasedTarget target : targets) {
      switch (selector) {
        case ALL:
          result.add(target);
          break;
        case PRODUCTION:
          if (!target.isTests()) {
            result.add(target);
          }
          break;
        case TEST:
          if (target.isTests()) {
            result.add(target);
          }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public <T extends BuildTarget<?>> List<T> getAllTargets(@NotNull BuildTargetType<T> type) {
    //noinspection unchecked
    return (List<T>)myTargets.get(type);
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

    final List<? extends BuildTarget<?>> allTargets = getAllTargets();
    TargetOutputIndex outputIndex = new TargetOutputIndexImpl(allTargets, context);
    for (BuildTarget<?> target : allTargets) {
      myDependencies.put(target, target.computeDependencies(this, outputIndex));
    }

    GraphGenerator<BuildTarget<?>>
      graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<BuildTarget<?>>() {
      @Override
      public Collection<BuildTarget<?>> getNodes() {
        return myAllTargets;
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

  @Override
  public List<? extends BuildTarget<?>> getAllTargets() {
    return myAllTargets;
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
}
