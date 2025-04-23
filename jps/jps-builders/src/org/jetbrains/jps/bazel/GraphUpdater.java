// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.jps.bazel.impl.SnapshotDeltaImpl;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.incremental.dependencies.LibraryDef;
import org.jetbrains.jps.javac.Iterators;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class GraphUpdater {
  private static final String MODULE_INFO_FILE_NAME = "module-info.java";
  private final String myTargetName;
  private final Set<NodeSource> myAllAffectedSources = new HashSet<>();

  public GraphUpdater(String targetName) {
    myTargetName = targetName;
  }

  public SourceSnapshotDelta updateDependencyGraph(DependencyGraph depGraph, SourceSnapshotDelta snapshotDelta, Delta delta, boolean errorsDetected) {
    if (snapshotDelta.isRecompileAll()) {
      if (errorsDetected || delta.isSourceOnly()) {
        // do nothing
        return new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());
      }
    }

    DifferentiateParameters params = DifferentiateParametersBuilder.create(myTargetName)
      .compiledWithErrors(errorsDetected)
      .calculateAffected(!snapshotDelta.isRecompileAll())
      .processConstantsIncrementally(true)
      .withAffectionFilter(s -> !LibraryDef.isLibraryPath(s))
      .withChunkStructureFilter(s -> true).get();

    DifferentiateResult diffResult = depGraph.differentiate(delta, params);

    if (snapshotDelta.isRecompileAll()) {
      depGraph.integrate(diffResult); // save full graph state
      return new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());
    }

    SourceSnapshotDelta nextSnapshotDelta = new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());

    if (!diffResult.isIncremental()) {
      // recompile whole target, no integrate necessary
      nextSnapshotDelta.markRecompileAll();
      return nextSnapshotDelta;
    }

    if (!errorsDetected && params.isCalculateAffected()) {
      // some compilers (and compiler plugins) may produce different outputs for the same set of inputs.
      // This might cause corresponding graph Nodes to be considered as always 'changed'. In some scenarios this may lead to endless build loops
      // This fallback logic detects such loops and recompiles the whole module chunk instead.
      Set<NodeSource> affectedForChunk = Iterators.collect(Iterators.filter(diffResult.getAffectedSources(), params.belongsToCurrentCompilationChunk()::test), new HashSet<>());
      if (!affectedForChunk.isEmpty() && !myAllAffectedSources.addAll(affectedForChunk)) {
        // all affected files in this round have already been affected in previous rounds. This might indicate a build cycle => recompiling whole chunk
        // todo: diagnostic
        //LOG.info("Build cycle detected for " + chunk.getName() + "; recompiling whole module chunk");
        // turn on non-incremental mode for the current target  => next time the whole target is recompiled and affected files won't be calculated anymore
        nextSnapshotDelta.markRecompileAll();
        return nextSnapshotDelta;
      }
    }

    for (NodeSource src : diffResult.getAffectedSources()) {

      if (isJavaModuleInfo(src)) {
        // recompile whole target, no integrate necessary
        nextSnapshotDelta.markRecompileAll();
        return nextSnapshotDelta;
      }

      nextSnapshotDelta.markRecompile(src);
    }
    
    if (delta.isSourceOnly()) {
      // the delta does not correspond to real compilation session, files already marked for recompilation should be marked for recompilation in the next snapshot too
      for (NodeSource source : snapshotDelta.getSourcesToRecompile()) {
        nextSnapshotDelta.markRecompile(source);
      }
    }

    if (!errorsDetected) {
      depGraph.integrate(diffResult);
    }

    return nextSnapshotDelta;
  }

  private static boolean isJavaModuleInfo(NodeSource src) {
    String path = src.toString();
    if (!path.endsWith(MODULE_INFO_FILE_NAME)) {
      return false;
    }
    if (path.length() > MODULE_INFO_FILE_NAME.length()) {
      char separator = path.charAt(path.length() - MODULE_INFO_FILE_NAME.length() - 1);
      return separator == '/' || separator == File.separatorChar;
    }
    return true;
  }
}
