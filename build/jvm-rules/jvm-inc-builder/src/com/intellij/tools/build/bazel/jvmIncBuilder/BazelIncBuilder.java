// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.DeltaView;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.LibraryGraphLoader;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jetbrains.jps.util.Iterators.*;

/** @noinspection SSBasedInspection*/
public class BazelIncBuilder {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.BazelIncBuilder");
  // recompile all, if more than X percent of files has been changed; for incremental tests, set equal to 100
  private static final int RECOMPILE_CHANGED_RATIO_PERCENT = 85;

  public ExitCode build(BuildContext context) {
    // todo: support cancellation checks
    // todo: additional diagnostics, if necessary

    DiagnosticSink diagnostic = context;
    NodeSourceSnapshotDelta srcSnapshotDelta = null;
    Iterable<NodeSource> modifiedLibraries = List.of();
    Iterable<NodeSource> deletedLibraries = List.of();

    try (StorageManager storageManager = new StorageManager(context)) {

      try {
        GraphUpdater graphUpdater = new GraphUpdater(context.getTargetName());

        LOG.info(() -> "Building " + context.getTargetName() + " (rebuild requested: " + context.isRebuild() + ")");

        if (context.isRebuild() || !storageManager.getOutputBuilder().isInputZipExist()) {
          // either rebuild is explicitly requested, or there is no previous data, need to compile the whole target
          srcSnapshotDelta = new SnapshotDeltaImpl(context.getSources());
          srcSnapshotDelta.markRecompileAll(); // force rebuild
        }
        else {
          ConfigurationState pastState = ConfigurationState.loadSavedState(context);
          ConfigurationState presentState = new ConfigurationState(context.getPathMapper(), context.getSources(), context.getBinaryDependencies(), context.getFlags());

          srcSnapshotDelta = new SnapshotDeltaImpl(pastState.getSources(), context.getSources());
          if (shouldRecompileAll(srcSnapshotDelta) || pastState.getFlagsDigest() != presentState.getFlagsDigest() || pastState.getClasspathStructureDigest() != presentState.getClasspathStructureDigest()) {
            int changedPercent = srcSnapshotDelta.getChangedPercent();
            LOG.info(() -> "Marking whole target for recompilation [" + context.getTargetName() + "]. Changed sources: " + changedPercent + "% (threshold " + RECOMPILE_CHANGED_RATIO_PERCENT + "%) ");
            srcSnapshotDelta.markRecompileAll();
          }
          else {
            Predicate<NodeSource> isLibTracked = ns -> DataPaths.isLibraryTracked(ns.toString());
            ElementSnapshotDeltaImpl<NodeSource> libsSnapshotDelta = new ElementSnapshotDeltaImpl<>(
              ElementSnapshot.derive(pastState.getLibraries(), isLibTracked),
              ElementSnapshot.derive(context.getBinaryDependencies(), isLibTracked)
            );
            modifiedLibraries = libsSnapshotDelta.getModified();
            deletedLibraries = libsSnapshotDelta.getDeleted();

            if (!isEmpty(modifiedLibraries)) {
              // differentiate library deps
              List<Graph> pastLibGraphs = new ArrayList<>();
              List<Graph> presentLibGraphs = new ArrayList<>();
              Set<NodeSource> changedLibNodeSources = new HashSet<>();
              Set<NodeSource> deletedLibNodeSources = new HashSet<>();
              for (NodeSource presentLib : modifiedLibraries) {
                Path presentLibPath = context.getPathMapper().toPath(presentLib);
                Path pastLibPath = DataPaths.getJarBackupStoreFile(context, presentLibPath);
                try {
                  Pair<NodeSourceSnapshot, Graph> presentGraph = LibraryGraphLoader.getLibraryGraph(presentLib, presentState.getLibraries().getDigest(presentLib), presentLibPath);
                  Pair<NodeSourceSnapshot, Graph> pastGraph = LibraryGraphLoader.getLibraryGraph(presentLib, pastState.getLibraries().getDigest(presentLib), pastLibPath);
                  NodeSourceSnapshotDelta delta = new SnapshotDeltaImpl(pastGraph.first, presentGraph.first);
                  if (!isEmpty(delta.getModified()) || !isEmpty(delta.getDeleted())) {
                    collect(delta.getModified(), changedLibNodeSources);
                    collect(delta.getDeleted(), deletedLibNodeSources);
                    pastLibGraphs.add(pastGraph.second); // all nodes of the past state should be available for analysis
                    presentLibGraphs.add(presentGraph.second);
                  }
                }
                catch (Exception e) { // problems loading library graphs
                  LOG.log(Level.WARNING, "Problems loading library graphs, recompiling whole target " + context.getTargetName(), e);
                  srcSnapshotDelta.markRecompileAll();
                  context.report(Message.create(null, Message.Kind.WARNING, e));
                  break;
                }
              }

              if (!changedLibNodeSources.isEmpty() || !deletedLibNodeSources.isEmpty()) {
                try {
                  Delta libDelta = new DeltaView(changedLibNodeSources, deletedLibNodeSources, CompositeGraph.create(presentLibGraphs));
                  srcSnapshotDelta = graphUpdater.updateBeforeCompilation(storageManager.getGraph(), srcSnapshotDelta, libDelta, pastLibGraphs);
                  if (shouldRecompileAll(srcSnapshotDelta)) {
                    srcSnapshotDelta.markRecompileAll();
                  }
                }
                catch (IOException e) {
                  LOG.log(Level.WARNING, "Problems loading dependency graph, recompiling whole target " + context.getTargetName(), e);
                  srcSnapshotDelta.markRecompileAll();
                  context.report(Message.create(null, Message.Kind.WARNING, e));
                }
              }
            }

            // for all modified forms ensure sources bound to forms are marked for recompilation
            if (!srcSnapshotDelta.isRecompileAll()) {
              Iterator<@NotNull NodeSource> modifiedForms = filter(srcSnapshotDelta.getModified(), FormBinding::isForm).iterator();
              if (modifiedForms.hasNext()) {
                for (NodeSource source : FormsCompiler.findBoundSources(storageManager, collect(modifiedForms, new ArrayList<>()))) {
                  srcSnapshotDelta.markRecompile(source);
                }
              }
            }

            // expand compile scope
            if (!srcSnapshotDelta.isRecompileAll()) {
              DependencyGraph graph = storageManager.getGraph();
              Delta sourceOnlyDelta = graph.createDelta(srcSnapshotDelta.getModified(), srcSnapshotDelta.getDeleted(), true);
              srcSnapshotDelta = graphUpdater.updateBeforeCompilation(graph, srcSnapshotDelta, sourceOnlyDelta, List.of());
              if (shouldRecompileAll(srcSnapshotDelta)) {
                srcSnapshotDelta.markRecompileAll();
              }
            }
          }
        }

        List<CompilerRunner> roundCompilers = collect(map(RunnerRegistry.getRoundCompilers(), f -> f.create(context, storageManager)), new ArrayList<>());
        boolean isInitialRound = true;

        do { // build rounds loop

          if (srcSnapshotDelta.isRecompileAll()) {
            storageManager.cleanBuildState();
            modifiedLibraries = ElementSnapshot.derive(context.getBinaryDependencies(), ns -> DataPaths.isLibraryTracked(ns.toString())).getElements();
            deletedLibraries = Set.of();
          }
          else {
            if (isInitialRound) {
              storageManager.cleanTrashDir();
            }
          }

          diagnostic = isInitialRound? new PostponedDiagnosticSink() : context; // for initial round postpone error reporting
          OutputSinkImpl outSink = new OutputSinkImpl(storageManager);

          if (isInitialRound) {
            // processing deleted sources makes sense on inintial round only
            if (!srcSnapshotDelta.isRecompileAll() && !isEmpty(srcSnapshotDelta.getDeleted())) {
              // clean outputs that correspond to deleted sources, no matter of source type
              Collection<String> cleaned = deleteCompilerOutputs(
                storageManager.getGraph(), srcSnapshotDelta.getDeleted(), storageManager.getCompositeOutputBuilder(), new ArrayList<>()
              );
              logDeletedPaths(context, cleaned);
            }
          }

          for (CompilerRunner runner : roundCompilers) {

            Iterable<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());

            if (!srcSnapshotDelta.isRecompileAll() && !isEmpty(toCompile)) {
              // delete outputs corresponding to recompiled sources before running the compiler
              ZipOutputBuilder outBuilder = storageManager.getCompositeOutputBuilder();
              Collection<String> cleaned = deleteCompilerOutputs(
                storageManager.getGraph(), toCompile, outBuilder, new ArrayList<>()
              );
              for (String toDelete : runner.getOutputPathsToDelete()) {
                if (outBuilder.deleteEntry(toDelete)) {
                  cleaned.add(toDelete);
                }
              }
              logDeletedPaths(context, cleaned);
            }

            ExitCode code = runner.compile(toCompile, filter(srcSnapshotDelta.getDeleted(), runner::canCompile), diagnostic, outSink);
            if (code == ExitCode.CANCEL) {
              return code;
            }
            if (code == ExitCode.ERROR && !diagnostic.hasErrors()) {
              // ensure we have some error message
              diagnostic.report(Message.error(runner, runner.getName() + " completed with errors"));
            }
            if (diagnostic.hasErrors()) {
              break;
            }
          }

          NodeSourceSnapshotDelta nextSnapshotDelta = graphUpdater.updateAfterCompilation(
            storageManager.getGraph(), srcSnapshotDelta, createGraphDelta(storageManager.getGraph(), srcSnapshotDelta, outSink), diagnostic.hasErrors()
          );

          if (!diagnostic.hasErrors()) {
            srcSnapshotDelta = nextSnapshotDelta;
          }
          else {
            if (srcSnapshotDelta.isRecompileAll() || !nextSnapshotDelta.hasChanges()) {
              return ExitCode.ERROR;
            }
            // keep previous snapshot delta, just augment it with the newly found sources for recompilation
            if (nextSnapshotDelta.isRecompileAll()) {
              srcSnapshotDelta.markRecompileAll();
            }
            else {
              for (NodeSource source : nextSnapshotDelta.getModified()) {
                srcSnapshotDelta.markRecompile(source);
              }
            }
            if (!isInitialRound) {
              return ExitCode.ERROR;
            }
            // for initial round, partial compilation and when analysis has expanded the scope, attempt automatic error recovery by repeating the compilation with the expanded scope
          }

          isInitialRound = false;
        }
        while (srcSnapshotDelta.hasChanges());
        
      }
      catch (Throwable e) {
        // catch and report all errors before the storage manager is closed
        LOG.log(Level.SEVERE, "Unexpected error compiling " + context.getTargetName(), e);
        diagnostic.report(Message.create(null, e));
        return ExitCode.ERROR;
      }
      finally {
        if (diagnostic instanceof PostponedDiagnosticSink) {
          // report postponed errors, if necessary; ensure all errors are reported before storages are closed
          ((PostponedDiagnosticSink)diagnostic).drainTo(context);
        }
      }

      return ExitCode.OK;
    }
    finally {
      saveBuildState(context, srcSnapshotDelta, modifiedLibraries, deletedLibraries);
    }
  }

  public void saveBuildState(
    BuildContext context, NodeSourceSnapshotDelta srcSnapshotDelta, Iterable<NodeSource> modifiedLibraries, Iterable<NodeSource> deletedLibraries
  ) {

    if (srcSnapshotDelta != null) {
      if (context.hasErrors()) {
        ConfigurationState pastState = ConfigurationState.loadSavedState(context);
        new ConfigurationState(context.getPathMapper(), srcSnapshotDelta.asSnapshot(), pastState.getLibraries(), context.getFlags()).save(context);
      }
      else {
        new ConfigurationState(context.getPathMapper(), srcSnapshotDelta.asSnapshot(), context.getBinaryDependencies(), context.getFlags()).save(context);
      }
    }

    if (!context.hasErrors()) {
      try { // backup current deps content if the build was successful
        Set<Path> presentPaths = collect(filter(map(modifiedLibraries, context.getPathMapper()::toPath), Files::exists), new HashSet<>());
        Set<Path> deletedPaths = collect(map(deletedLibraries, context.getPathMapper()::toPath), new HashSet<>());
        Path outputZip = context.getOutputZip();
        if (Files.exists(outputZip)) {
          presentPaths.add(outputZip);
        }
        else {
          deletedPaths.add(outputZip);
        }
        Path abiOut = context.getAbiOutputZip();
        if (abiOut != null) {
          if (Files.exists(abiOut)) {
            presentPaths.add(abiOut);
          }
          else {
            deletedPaths.add(abiOut);
          }
        }
        StorageManager.backupDependencies(context, deletedPaths, presentPaths);
      }
      catch (Throwable e) {
        LOG.log(Level.SEVERE, "Error saving build state " + context.getTargetName(), e);
        context.report(Message.create(null, e));
      }
    }
  }

  private static boolean shouldRecompileAll(NodeSourceSnapshotDelta srcSnapshotDelta) {
    return srcSnapshotDelta.isRecompileAll() || srcSnapshotDelta.getChangedPercent() > RECOMPILE_CHANGED_RATIO_PERCENT;
  }

  private static Collection<String> deleteCompilerOutputs(
    DependencyGraph depGraph, Iterable<@NotNull NodeSource> sourcesToCompile, ZipOutputBuilder outBuilder, Collection<String> deletedPathsAcc
  ) {
    for (Node<?, ?> node : filter(flat(map(sourcesToCompile, depGraph::getNodes)), n -> n instanceof JVMClassNode)) {
      String outputPath = ((JVMClassNode<?, ?>) node).getOutFilePath();
      if (outBuilder.deleteEntry(outputPath)) {
        deletedPathsAcc.add(outputPath);
      }
    }
    return deletedPathsAcc;
  }

  private static void logDeletedPaths(BuildContext context, Iterable<String> deletedPaths) {
    if (!context.isRebuild()) {
      BuildProcessLogger logger = context.getBuildLogger();
      if (logger.isEnabled() && !isEmpty(deletedPaths)) {
        logger.logDeletedPaths(deletedPaths);
      }
    }
  }

  private static Delta createGraphDelta(DependencyGraph depGraph, NodeSourceSnapshotDelta snapshotDelta, OutputSinkImpl outSink) {
    Delta delta = depGraph.createDelta(snapshotDelta.getModified(), snapshotDelta.getDeleted(), false);
    for (var pair : outSink.getNodes()) {
      delta.associate(pair.node(), pair.sources());
    }
    return delta;
  }

}
