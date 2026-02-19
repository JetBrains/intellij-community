// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildDiagnosticCollector;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.SnapshotDeltaImpl;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

import static org.jetbrains.jps.util.Iterators.*;

public final class GraphUpdater {
  private static final String MODULE_INFO_FILE_NAME = "module-info.java";
  private final String myTargetName;
  private final Set<NodeSource> myAllAffectedSources = new HashSet<>();

  public GraphUpdater(String targetName) {
    myTargetName = targetName;
  }

  public NodeSourceSnapshotDelta updateBeforeCompilation(DependencyGraph depGraph, NodeSourceSnapshotDelta snapshotDelta, Delta delta, List<Graph> extParts, @Nullable BuildDiagnosticCollector diagnosticBuilder) {
    Set<NodeSource> modifiedBefore = diagnosticBuilder == null? Set.of() : collect(snapshotDelta.getModified(), new HashSet<>());
    StringBuilder logData = diagnosticBuilder == null? null : new StringBuilder();
    NodeSourceSnapshotDelta nextSnapshotDelta = null;
    try {
      return nextSnapshotDelta = updateDependencyGraph(depGraph, snapshotDelta, delta, false, extParts, false, logData == null? LogConsumer.EMPTY : LogConsumer.memory(logData));
    }
    finally {
      if (diagnosticBuilder != null) {
        if (!delta.isSourceOnly()) {
          diagnosticBuilder.markLibrariesDifferentiateEnd();
        }
        List<NodeSource> affected = nextSnapshotDelta == null? List.of() : collect(filter(nextSnapshotDelta.getModified(), src -> !modifiedBefore.contains(src)), new ArrayList<>());
        String decisionLog = logData.toString();
        if (delta.isSourceOnly()) {
          diagnosticBuilder.setSourcesDifferentiateLog(affected, decisionLog);
        }
        else {
          diagnosticBuilder.setLibrariesDifferentiateLog(affected, decisionLog);
        }
      }
    }
  }

  public NodeSourceSnapshotDelta updateAfterCompilation(DependencyGraph depGraph, NodeSourceSnapshotDelta snapshotDelta, Delta delta, boolean errorsDetected, @Nullable BuildDiagnosticCollector diagnosticBuilder) {
    List<NodeSource> modifiedBefore = diagnosticBuilder == null? List.of() : collect(snapshotDelta.getModified(), new ArrayList<>());
    StringBuilder logData = diagnosticBuilder == null? null : new StringBuilder();
    try {
      return updateDependencyGraph(depGraph, snapshotDelta, delta, errorsDetected, List.of(), true, logData == null? LogConsumer.EMPTY : LogConsumer.memory(logData));
    }
    finally {
      if (diagnosticBuilder != null) {
        diagnosticBuilder.addRoundData(modifiedBefore, logData.toString());
      }
    }
  }
  
  private NodeSourceSnapshotDelta updateDependencyGraph(DependencyGraph depGraph, NodeSourceSnapshotDelta snapshotDelta, Delta delta, boolean errorsDetected, List<Graph> extParts, boolean isAfterCompilation, LogConsumer logConsumer) {
    if (snapshotDelta.isRecompileAll()) {
      if (isAfterCompilation) {
        if (errorsDetected) {
          // do nothing
          return new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());
        }
      }
      else {
        if (delta.isSourceOnly()) {
          return snapshotDelta;
        }
      }
    }

    NodeSourceSnapshot baseSnapshot = snapshotDelta.getBaseSnapshot();
    Predicate<NodeSource> currentChunkScopeFilter = s -> contains(baseSnapshot.getElements(), s);
    DifferentiateParameters params = DifferentiateParametersBuilder.create(myTargetName)
      .compiledWithErrors(errorsDetected)
      .calculateAffected(!snapshotDelta.isRecompileAll())
      .processConstantsIncrementally(true)
      .withScopeFilter(__-> true)
      .withAffectionFilter(currentChunkScopeFilter)
      .withChunkStructureFilter(currentChunkScopeFilter)
      .withLogConsumer(LogConsumer.composite(LogConsumer.createJULogConsumer(Level.FINE), logConsumer)) 
      .get();

    DifferentiateResult diffResult = depGraph.differentiate(delta, params, extParts);

    if (snapshotDelta.isRecompileAll() && isAfterCompilation) {
      depGraph.integrate(diffResult); // save full graph state
      return new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());
    }

    NodeSourceSnapshotDelta nextSnapshotDelta;
    if (isAfterCompilation) {
      nextSnapshotDelta = new SnapshotDeltaImpl(snapshotDelta.getBaseSnapshot());
    }
    else {
      // the delta does not correspond to real compilation session,
      // mark files for recompilation in the existing delta and keep information about deleted files or already modified files
      nextSnapshotDelta = snapshotDelta;
    }

    if (!diffResult.isIncremental()) {
      // recompile whole target, no integrate necessary
      nextSnapshotDelta.markRecompileAll();
      return nextSnapshotDelta;
    }

    if (!errorsDetected && params.isCalculateAffected()) {
      // some compilers (and compiler plugins) may produce different outputs for the same set of inputs.
      // This might cause corresponding graph Nodes to be considered as always 'changed'. In some scenarios this may lead to endless build loops
      // This fallback logic detects such loops and recompiles the whole module chunk instead.
      Set<NodeSource> affectedForChunk = collect(filter(diffResult.getAffectedSources(), params.belongsToCurrentCompilationChunk()), new HashSet<>());
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

    if (!errorsDetected && isAfterCompilation) {
      // do not integrate delta, if compilation has not happened, to keep information about deleted paths
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
