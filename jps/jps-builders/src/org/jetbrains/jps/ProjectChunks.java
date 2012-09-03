package org.jetbrains.jps;

import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class ProjectChunks {
  private List<ModuleChunk> chunks;
  private List<ModuleBuildTarget> myAllTargets;
  private Map<ModuleBuildTarget, Collection<ModuleBuildTarget>> myTargetDependencies;
  private final JpsProject myProject;

  public ProjectChunks(JpsProject project) {
    myProject = project;
  }

  public List<ModuleChunk> getChunkList() {
    initializeChunks();
    return chunks;
  }

  private void initializeChunks() {
    if (chunks != null) {
      return;
    }

    myAllTargets = new ArrayList<ModuleBuildTarget>(myProject.getModules().size()*2);
    for (JpsModule module : myProject.getModules()) {
      myAllTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
      myAllTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
    }
    myTargetDependencies = new HashMap<ModuleBuildTarget, Collection<ModuleBuildTarget>>();
    for (ModuleBuildTarget target : myAllTargets) {
      myTargetDependencies.put(target, target.computeDependencies());
    }

    GraphGenerator<ModuleBuildTarget> graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<ModuleBuildTarget>() {
        @Override
        public Collection<ModuleBuildTarget> getNodes() {
          return myAllTargets;
        }

        @Override
        public Iterator<ModuleBuildTarget> getIn(ModuleBuildTarget n) {
          return myTargetDependencies.get(n).iterator();
        }
      }));
    
    final DFSTBuilder<ModuleBuildTarget> builder = new DFSTBuilder<ModuleBuildTarget>(graph);
    final TIntArrayList sccs = builder.getSCCs();

    chunks = new ArrayList<ModuleChunk>(sccs.size());
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        final Set<ModuleBuildTarget> chunkNodes = new LinkedHashSet<ModuleBuildTarget>();
        boolean test = false;
        for (int j = 0; j < size; j++) {
          final ModuleBuildTarget node = builder.getNodeByTNumber(myTNumber + j);
          test = node.isTests();//production target cannot depend on test so this flag is the same for all nodes in the cycle
          chunkNodes.add(node);
        }
        chunks.add(new ModuleChunk(chunkNodes, test));

        myTNumber += size;
        return true;
      }
    });
  }

  public Collection<ModuleBuildTarget> getAllTargets() {
    initializeChunks();
    return myAllTargets;
  }

  public Set<ModuleBuildTarget> getDependenciesRecursively(ModuleBuildTarget target) {
    initializeChunks();
    LinkedHashSet<ModuleBuildTarget> result = new LinkedHashSet<ModuleBuildTarget>();
    for (ModuleBuildTarget dep : myTargetDependencies.get(target)) {
      collectDependenciesRecursively(dep, result);
    }
    return result;
  }

  private void collectDependenciesRecursively(ModuleBuildTarget target, LinkedHashSet<ModuleBuildTarget> result) {
    if (result.add(target)) {
      for (ModuleBuildTarget dep : myTargetDependencies.get(target)) {
        collectDependenciesRecursively(dep, result);
      }
    }
  }
}
