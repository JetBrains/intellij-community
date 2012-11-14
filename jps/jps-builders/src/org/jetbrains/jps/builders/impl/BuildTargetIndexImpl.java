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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class BuildTargetIndexImpl implements BuildTargetIndex {
  private Map<BuildTargetType<?>, List<? extends BuildTarget<?>>> myTargets;
  private Map<BuildTarget<?>, Collection<BuildTarget<?>>> myDependencies;
  private List<BuildTargetChunk> myTargetChunks;
  private final List<BuildTarget<?>> myAllTargets;

  public BuildTargetIndexImpl(@NotNull JpsModel model) {
    myTargets = new THashMap<BuildTargetType<?>, List<? extends BuildTarget<?>>>();
    List<List<? extends BuildTarget<?>>> targetsByType = new ArrayList<List<? extends BuildTarget<?>>>();
    for (BuildTargetType<?> type : BuilderRegistry.getInstance().getTargetTypes()) {
      List<? extends BuildTarget<?>> targets = type.computeAllTargets(model);
      myTargets.put(type, targets);
      targetsByType.add(targets);
    }
    myDependencies = new THashMap<BuildTarget<?>, Collection<BuildTarget<?>>>();
    myAllTargets = Collections.unmodifiableList(ContainerUtil.concat(targetsByType));
  }

  @Override
  public Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@Nullable JpsModule module, @NotNull ModuleTargetSelector selector) {
    final List<ModuleBasedTarget<?>> result = new SmartList<ModuleBasedTarget<?>>();
    for (BuildTarget<?> target : getAllTargets()) {
      if (!(target instanceof ModuleBasedTarget)) {
        continue;
      }
      final ModuleBasedTarget _target = (ModuleBasedTarget)target;
      if (module != null && !module.equals(_target.getModule())) {
        continue;
      }
      switch (selector) {
        case ALL:
          result.add(_target);
          break;
        case PRODUCTION:
          if (!_target.isTests()) {
            result.add(_target);
          }
          break;
        case TEST:
          if (_target.isTests()) {
            result.add(_target);
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
  public List<BuildTargetChunk> getSortedTargetChunks() {
    initializeChunks();
    return myTargetChunks;
  }


  private synchronized void initializeChunks() {
    if (myTargetChunks != null) {
      return;
    }

    final List<? extends BuildTarget<?>> allTargets = getAllTargets();
    for (BuildTarget<?> target : allTargets) {
      myDependencies.put(target, target.computeDependencies(this));
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
  public Set<BuildTarget<?>> getDependenciesRecursively(BuildTarget<?> target) {
    initializeChunks();
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
  public Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target) {
    initializeChunks();
    return myDependencies.get(target);
  }
}
