// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BatchBuildProcessLogger;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildDiagnosticCollector;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ConfigurationState;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ElementSnapshotDeltaImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.FormsCompiler;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.OutputSinkImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.PostponedDiagnosticSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ResourcesSnapshotDelta;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.RunnerRegistry;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.SnapshotDeltaImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.AsyncLibraryGraphLoader;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.DeltaView;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.CompositeGraph;
import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.util.Pair;
import org.jetbrains.jps.util.SystemInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.contains;
import static org.jetbrains.jps.util.Iterators.count;
import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.flat;
import static org.jetbrains.jps.util.Iterators.isEmpty;
import static org.jetbrains.jps.util.Iterators.map;

/** @noinspection SSBasedInspection*/
public class BazelIncBuilder {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.BazelIncBuilder");
  // recompile all, if more than X percent of files has been changed; for incremental tests, set equal to 100
  private static final int RECOMPILE_CHANGED_RATIO_PERCENT = VMFlags.getChangesPercentToRebuild();
  private static final boolean COLLECT_BUILD_DIAGNOSTICS = true;

  public ExitCode build(BuildContext context) {
    // todo: support cancellation checks

    Iterable<String> unexpectedInputs = context.getUnexpectedInputs();
    if (!isEmpty(unexpectedInputs)) {
      StringBuilder msg = new StringBuilder("Unexpected inputs specified for the worker:");
      for (String input : unexpectedInputs) {
        msg.append("\n").append(input);
      }
      context.report(Message.error(null, msg.toString()));
      return ExitCode.ERROR;
    }

    DiagnosticSink diagnostic = context;
    NodeSourceSnapshotDelta srcSnapshotDelta = null;
    ResourcesSnapshotDelta resourcesDelta = null;
    Iterable<NodeSource> modifiedLibraries = List.of();
    Iterable<NodeSource> deletedLibraries = List.of();
    ConfigurationState pastState = null;
    ConfigurationState presentState = null;

    try (StorageManager storageManager = new StorageManager(context)) {

      BuildDiagnosticCollector diagnosticCollector = COLLECT_BUILD_DIAGNOSTICS? new BuildDiagnosticCollector(context) : null;
      try {
        GraphUpdater graphUpdater = new GraphUpdater(context.getTargetName());

        LOG.info(() -> "Building " + context.getTargetName() + " (rebuild requested: " + context.isRebuild() + ")");

        if (context.isRebuild()) {
          srcSnapshotDelta = new SnapshotDeltaImpl(context.getSources());
          srcSnapshotDelta.markRecompileAll(); // force rebuild
        }
        else {
          pastState = ConfigurationState.loadSavedState(context);
          presentState = new ConfigurationState(
            context.getPathMapper(), context.getSources(), context.getResources(), context.getBinaryDependencies(), context.getFlags(), context.getUntrackedInputsDigest()
          );

          srcSnapshotDelta = new SnapshotDeltaImpl(pastState.getSources(), presentState.getSources());

          if (shouldRecompileAll(srcSnapshotDelta) || pastState.digestsDiffer(presentState) || !Files.exists(DataPaths.getJarBackupStoreFile(context, context.getOutputZip()) /*previous output state is missing*/)) {
            int changedPercent = srcSnapshotDelta.getChangedPercent();
            LOG.info(() -> "Marking whole target for recompilation [" + context.getTargetName() + "]. Changed sources: " + changedPercent + "% (threshold " + RECOMPILE_CHANGED_RATIO_PERCENT + "%) ");
            srcSnapshotDelta.markRecompileAll();
          }
          else {
            if (diagnosticCollector != null) {
              diagnosticCollector.markLibrariesDifferentiateBegin();
            }
            Predicate<NodeSource> isLibTracked = ns -> DataPaths.isLibraryTracked(ns.toString());
            ElementSnapshotDeltaImpl<NodeSource> libsSnapshotDelta = new ElementSnapshotDeltaImpl<>(
              ElementSnapshot.derive(pastState.getLibraries(), isLibTracked),
              ElementSnapshot.derive(presentState.getLibraries(), isLibTracked)
            );
            modifiedLibraries = libsSnapshotDelta.getModified();
            deletedLibraries = libsSnapshotDelta.getDeleted();

            if (!isEmpty(modifiedLibraries)) {
              // differentiate library deps
              List<Graph> pastLibGraphs = new ArrayList<>();
              List<Graph> presentLibGraphs = new ArrayList<>();
              Set<NodeSource> changedLibNodeSources = new HashSet<>();
              Set<NodeSource> deletedLibNodeSources = new HashSet<>();
              for (AsyncLibraryGraphLoader.GraphStateChange state : AsyncLibraryGraphLoader.submit(context, modifiedLibraries, pastState.getLibraries(), presentState.getLibraries())) {
                if (srcSnapshotDelta.isRecompileAll()) {
                  state.cancel(); // graph analysis not needed anymore
                  continue;
                }
                try {
                  Pair<NodeSourceSnapshot, Graph> pastGraph = state.getPast();
                  Pair<NodeSourceSnapshot, Graph> presentGraph = state.getPresent();
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
                }
              }

              if (!srcSnapshotDelta.isRecompileAll() && (!changedLibNodeSources.isEmpty() || !deletedLibNodeSources.isEmpty())) {

                // Add to 'pastLibGraphs' all previously available graph parts, even if they are not changed. Reason: need full nodes info for graph node traversals
                for (AsyncLibraryGraphLoader.GraphState state : AsyncLibraryGraphLoader.submit(libsSnapshotDelta.getBaseSnapshot(), lib -> !contains(libsSnapshotDelta.getModified(), lib), context.getPathMapper()::toPath)) {
                  if (srcSnapshotDelta.isRecompileAll()) {
                    state.cancel(); // graph analysis not needed anymore
                    continue;
                  }
                  try {
                    pastLibGraphs.add(state.get().second);
                  }
                  catch (Exception e) {
                    LOG.log(Level.WARNING, "Problems loading library graphs, recompiling whole target " + context.getTargetName(), e);
                    srcSnapshotDelta.markRecompileAll();
                    context.report(Message.create(null, Message.Kind.WARNING, e));
                  }
                }

                if (!srcSnapshotDelta.isRecompileAll()) {
                  try {
                    Delta libDelta = new DeltaView(changedLibNodeSources, deletedLibNodeSources, CompositeGraph.create(presentLibGraphs));
                    srcSnapshotDelta = graphUpdater.updateBeforeCompilation(storageManager.getGraph(), srcSnapshotDelta, libDelta, pastLibGraphs, diagnosticCollector);
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
              srcSnapshotDelta = graphUpdater.updateBeforeCompilation(graph, srcSnapshotDelta, sourceOnlyDelta, List.of(), diagnosticCollector);
              if (shouldRecompileAll(srcSnapshotDelta)) {
                srcSnapshotDelta.markRecompileAll();
              }
            }
          }

          if (!srcSnapshotDelta.isRecompileAll()) {
            resourcesDelta = new ResourcesSnapshotDelta(pastState.getResources(), presentState.getResources());
          }
        }

        List<CompilerRunner> roundCompilers = collect(map(RunnerRegistry.getRoundCompilers(), f -> f.create(context, storageManager)), new ArrayList<>());
        boolean isInitialRound = true;

        do { // build rounds loop

          if (srcSnapshotDelta.isRecompileAll()) {
            if (isInitialRound && diagnosticCollector != null) {
              diagnosticCollector.setWholeTargetRebuild(true);
            }
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

            // processing resources
            ZipOutputBuilder out = storageManager.getOutputBuilder();
            if (resourcesDelta == null || srcSnapshotDelta.isRecompileAll()) {
              // copy everything
              for (ResourceGroup group : context.getResources()) {
                copyResources(group, context.getPathMapper(), out);
              }
            }
            else {
              // only copy modified and remove deleted
              deleteResources(resourcesDelta.getPastResources(), flat(resourcesDelta.getDeleted(), resourcesDelta.getChanged()), out);
              copyResources(resourcesDelta.getPresentResources(), resourcesDelta.getModified(), context.getPathMapper(), out);
            }

            // processing deleted sources makes sense on inintial round only
            if (!srcSnapshotDelta.isRecompileAll() && !isEmpty(srcSnapshotDelta.getDeleted())) {
              // clean outputs that correspond to deleted sources, no matter of source type
              Collection<String> cleaned = deleteCompilerOutputs(
                storageManager.getGraph(), srcSnapshotDelta.getDeleted(), storageManager.getCompositeOutputBuilder(), new ArrayList<>()
              );
              logDeletedPaths(context, cleaned);
            }
          }
          else {
            if (srcSnapshotDelta.isRecompileAll()) {
              // After several rounds, the IC logic can decide to recompile everything
              ZipOutputBuilder out = storageManager.getOutputBuilder();
              for (ResourceGroup group : context.getResources()) {
                copyResources(group, context.getPathMapper(), out);
              }
            }
          }

          for (CompilerRunner runner : roundCompilers) {

            Iterable<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
            if (context.getBuildLogger() instanceof BatchBuildProcessLogger batchLogger) {
              batchLogger.startBatch();
            }

            if (!srcSnapshotDelta.isRecompileAll() && !isEmpty(toCompile)) {
              // delete outputs corresponding to recompiled sources before running the compiler
              ZipOutputBuilder outBuilder = storageManager.getCompositeOutputBuilder();
              Collection<String> cleaned = deleteCompilerOutputs(
                storageManager.getGraph(), toCompile, outBuilder, new ArrayList<>()
              );
              logDeletedPaths(context, cleaned);
            }

            ExitCode code = runner.compile(toCompile, filter(srcSnapshotDelta.getDeleted(), runner::canCompile), diagnostic, outSink);

            if (context.getBuildLogger() instanceof BatchBuildProcessLogger batchLogger) {
              batchLogger.stopBatch();
            }

            if (code == ExitCode.CANCEL) {
              if (!srcSnapshotDelta.isRecompileAll()) {
                // in case of errors, clean partially compiled output to maintain consistent state
                deleteGeneratedOutputs(outSink, storageManager.getCompositeOutputBuilder());
              }
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
            storageManager.getGraph(), srcSnapshotDelta, createGraphDelta(storageManager.getGraph(), srcSnapshotDelta, outSink), diagnostic.hasErrors(), diagnosticCollector
          );

          if (!diagnostic.hasErrors()) {
            srcSnapshotDelta = nextSnapshotDelta;
          }
          else {
            if (srcSnapshotDelta.isRecompileAll()) {
              // no need to clean partial outputs, the next build will start from the clean state anyway
              return ExitCode.ERROR;
            }

            if (nextSnapshotDelta.hasChanges()) {
              // keep previous snapshot delta, just augment it with the newly found sources for recompilation
              if (nextSnapshotDelta.isRecompileAll()) {
                srcSnapshotDelta.markRecompileAll();
              }
              else {
                for (NodeSource source : nextSnapshotDelta.getModified()) {
                  srcSnapshotDelta.markRecompile(source);
                }
              }
            }

            if (!isInitialRound || !nextSnapshotDelta.hasChanges()) {
              // in case of errors, clean partially compiled output to maintain consistent state
              deleteGeneratedOutputs(outSink, storageManager.getCompositeOutputBuilder());
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
          ((PostponedDiagnosticSink) diagnostic).drainTo(context);
        }
        if (diagnosticCollector != null) {
          diagnosticCollector.writeData(pastState, presentState);
        }
      }

    }
    catch (Throwable e) {
      // catch any unexpected errors happened closing storages
      context.report(Message.create(null, e));
      return ExitCode.ERROR;
    }
    finally {
      NodeSourceSnapshot sourcesState = srcSnapshotDelta != null? srcSnapshotDelta.asSnapshot() : null;
      saveBuildState(
        context, sourcesState, context.getResources(), modifiedLibraries, deletedLibraries
      );
    }

    return context.hasErrors()? ExitCode.ERROR : ExitCode.OK;
  }

  private static void deleteResources(Iterable<ResourceGroup> resGroups, Iterable<NodeSource> resources, ZipOutputBuilder out) {
    if (count(resGroups) == 1) {
      // optimization
      ResourceGroup resourceGroup = resGroups.iterator().next();
      for (NodeSource res : resources) {
        deleteResource(resourceGroup, res, out);
      }
    }
    else {
      for (NodeSource res : resources) {
        for (ResourceGroup resourceGroup : filter(resGroups, gr -> contains(gr.getElements(), res))) {
          deleteResource(resourceGroup, res, out);
        }
      }
    }
  }

  private static void deleteResource(ResourceGroup resourceGroup, NodeSource res, ZipOutputBuilder out) {
    String destPath = getResourceDestinationPath(resourceGroup, res);
    if (destPath != null) {
      out.deleteEntry(destPath);
    }
  }

  private static void copyResources(Iterable<ResourceGroup> resGroups, Iterable<NodeSource> resources, NodeSourcePathMapper pathMapper, ZipOutputBuilder out) throws IOException {
    if (count(resGroups) == 1) {
      // optimization
      ResourceGroup resourceGroup = resGroups.iterator().next();
      for (NodeSource res : resources) {
        copyResource(resourceGroup, res, pathMapper, out);
      }
    }
    else {
      for (NodeSource res : resources) {
        for (ResourceGroup resourceGroup : filter(resGroups, gr -> contains(gr.getElements(), res))) {
          copyResource(resourceGroup, res, pathMapper, out);
        }
      }
    }
  }

  private static void copyResources(ResourceGroup resourceGroup, NodeSourcePathMapper pathMapper, ZipOutputBuilder out) throws IOException {
    for (NodeSource res : resourceGroup.getElements()) {
      copyResource(resourceGroup, res, pathMapper, out);
    }
  }

  private static void copyResource(ResourceGroup resourceGroup, NodeSource res, NodeSourcePathMapper pathMapper, ZipOutputBuilder out) throws IOException {
    String destPath = getResourceDestinationPath(resourceGroup, res);
    if (destPath != null) {
      out.putEntry(destPath, pathMapper.toPath(res)); // copy resources lazily
    }
  }

  private static @Nullable String getResourceDestinationPath(ResourceGroup group, NodeSource source) {
    String destPath = source.toString();

    String stripPrefix = group.getStripPrefix();
    if (!stripPrefix.isEmpty()) {
      if (destPath.length() > stripPrefix.length() && destPath.regionMatches(!SystemInfo.isFileSystemCaseSensitive, 0, stripPrefix, 0, stripPrefix.length()) && destPath.charAt(stripPrefix.length()) == '/' ) {
        destPath = destPath.substring(stripPrefix.length() + 1);
      }
      else {
        return null; // todo: emit error
      }
    }

    String addPrefix = group.getAddPrefix();
    if (!addPrefix.isEmpty()) {
      destPath = addPrefix + "/" + destPath;
    }

    return destPath;
  }


  public void saveBuildState(
    BuildContext context,
    NodeSourceSnapshot sourcesState, Iterable<ResourceGroup> resourcesState,
    Iterable<NodeSource> modifiedLibraries, Iterable<NodeSource> deletedLibraries
  ) {

    if (sourcesState == null) {
      return; // nothing is done
    }
    try {
      Set<Path> presentPaths = collect(filter(map(modifiedLibraries, context.getPathMapper()::toPath), Files::exists), new HashSet<>());
      Set<Path> deletedPaths = collect(map(deletedLibraries, context.getPathMapper()::toPath), new HashSet<>());
      Path outputZip = context.getOutputZip();
      Path abiOut = context.getAbiOutputZip();

      Stream.of(outputZip, abiOut, context.getKotlinCriStoragePath()).filter(Objects::nonNull).forEach(path -> {
        if (Files.exists(path)) {
          presentPaths.add(path);
        }
        else {
          deletedPaths.add(path);
        }
      });

      StorageManager.backupDependencies(context, deletedPaths, presentPaths);

      if (context.hasErrors()) {
        // in case of errors, rollback to previous resources state to ensure that
        // all resources deleted or changed for this compile session will be handled in the next session
        ConfigurationState pastState = ConfigurationState.loadSavedState(context);
        resourcesState = pastState.getResources();

        // do not publish incomplete artifacts
        Utils.deleteIfExists(outputZip);
        if (abiOut != null) {
          Utils.deleteIfExists(abiOut);
        }
      }

      // at this point saved build state contains all successfully compiled files and classes
      new ConfigurationState(
        context.getPathMapper(), sourcesState, resourcesState, context.getBinaryDependencies(), context.getFlags(), context.getUntrackedInputsDigest()
      ).save(context);

      BuildProcessLogger buildLogger = context.getBuildLogger();
      if (buildLogger.isEnabled()) {
        // in test mode, save build log for tests
        Files.writeString(
          DataPaths.getBuildProcessLoggerDataPath(context),
          buildLogger.getCollectedData(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
        );
      }
    }
    catch (Throwable e) {
      LOG.log(Level.SEVERE, "Error saving build state " + context.getTargetName(), e);
      context.report(Message.create(null, e));

      // Cannot guarantee build state data consistency: delete config store file => this will effectively cause target rebuild on next build
      try {
        Utils.deleteIfExists(DataPaths.getConfigStateStoreFile(context));
      }
      catch (Throwable ex) {
        LOG.log(Level.SEVERE, "Error clearing build state file for " + context.getTargetName(), ex);
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

  private static void deleteGeneratedOutputs(OutputSink sink, ZipOutputBuilder outBuilder) {
    for (String outputPath : flat(map(EnumSet.allOf(OutputOrigin.Kind.class), origin -> sink.getGeneratedOutputPaths(origin, OutputFile.Kind.bytecode)))) {
      outBuilder.deleteEntry(outputPath);
    }
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
