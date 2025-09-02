// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.DeltaView;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.LibraryGraphLoader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.BytecodeInstrumenter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.util.Pair;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.*;

/** @noinspection SSBasedInspection*/
public class BazelIncBuilder {
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

        if (context.isRebuild() || !storageManager.getOutputBuilder().isInputZipExist()) {
          // either rebuild is explicitly requested, or there is no previous data, need to compile whole target
          srcSnapshotDelta = new SnapshotDeltaImpl(context.getSources());
          srcSnapshotDelta.markRecompileAll(); // force rebuild
        }
        else {
          ConfigurationState pastState = ConfigurationState.loadSavedState(context);
          ConfigurationState presentState = new ConfigurationState(context.getPathMapper(), context.getSources(), context.getBinaryDependencies());

          srcSnapshotDelta = new SnapshotDeltaImpl(pastState.getSources(), context.getSources());
          if (shouldRecompileAll(srcSnapshotDelta) || pastState.getClasspathStructureDigest() != presentState.getClasspathStructureDigest()) {
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
                  srcSnapshotDelta.markRecompileAll();
                  context.report(Message.create(null, Message.Kind.WARNING, e));
                }
              }
            }

            // for all modified forms ensure sources bound to forms are marked for recompilation
            if (!srcSnapshotDelta.isRecompileAll()) {
              for (NodeSource source : FormsCompiler.findBoundSources(storageManager, filter(srcSnapshotDelta.getModified(), FormBinding::isForm))) {
                srcSnapshotDelta.markRecompile(source);
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

        List<CompilerRunner> compilers = collect(map(RunnerRegistry.getCompilers(), f -> f.create(context, storageManager)), new ArrayList<>());
        List<CompilerRunner> roundCompilers = collect(map(RunnerRegistry.getRoundCompilers(), f -> f.create(context, storageManager)), new ArrayList<>());
        List<BytecodeInstrumenter> instrumenters = collect(map(RunnerRegistry.getIntrumenters(), f -> f.create(context, storageManager)), new ArrayList<>());

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
            if (!srcSnapshotDelta.isRecompileAll()) {
              List<String> deletedPaths = new ArrayList<>();
              for (NodeSource source : filter(flat(srcSnapshotDelta.getDeleted(), srcSnapshotDelta.getModified()), s -> find(compilers, compiler -> compiler.canCompile(s)) != null)) {
                // source paths are assumed to be relative to source roots, so under the output root the directory structure is the same
                String path = source.toString();
                if (storageManager.getOutputBuilder().deleteEntry(path)) {
                  deletedPaths.add(path);
                }
              }
              logDeletedPaths(context, deletedPaths);
            }

            for (CompilerRunner runner : compilers) {
              List<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
              if (toCompile.isEmpty()) {
                continue;
              }

              runner.compile(toCompile, filter(srcSnapshotDelta.getDeleted(), runner::canCompile), diagnostic, outSink);

              if (diagnostic.hasErrors()) {
                break;
              }
            }
          }

          if (!diagnostic.hasErrors()) {
            if (!srcSnapshotDelta.isRecompileAll()) {
              // delete outputs corresponding to deleted or recompiled sources
              cleanOutputsForCompiledFiles(context, srcSnapshotDelta, storageManager.getGraph(), roundCompilers, storageManager.getCompositeOutputBuilder());
            }

            for (CompilerRunner runner : roundCompilers) {
              List<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
              if (toCompile.isEmpty()) {
                continue;
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
          }

          if (!diagnostic.hasErrors()) {
            runInstrumenters(outSink, storageManager, instrumenters, diagnostic);
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
        // catch and report all errors before the sotrage manager is closed
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

  private static void runInstrumenters(OutputSinkImpl outSink, StorageManager storageManager, List<BytecodeInstrumenter> instrumenters, DiagnosticSink diagnostic) throws IOException {
    for (OutputOrigin.Kind originKind : OutputOrigin.Kind.values()) {
      for (String generatedFile : outSink.getGeneratedOutputPaths(originKind, OutputFile.Kind.bytecode)) {
        boolean changes = false;
        byte[] content = null;
        ClassReader reader = null;
        for (BytecodeInstrumenter instrumenter : filter(instrumenters, inst -> inst.getSupportedOrigins().contains(originKind))) {
          if (content == null) {
            content = outSink.getFileContent(generatedFile);
          }
          try {
            if (reader == null) {
              reader = new FailSafeClassReader(content);
            }
            InstrumentationClassFinder classFinder = storageManager.getInstrumentationClassFinder();
            int version = InstrumenterClassWriter.getClassFileVersion(reader);
            ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), classFinder);
            final byte[] instrumented = instrumenter.instrument(generatedFile, reader, writer, classFinder);
            if (instrumented != null) {
              changes = true;
              content = instrumented;
              classFinder.cleanCachedData(reader.getClassName());
              reader = null;
            }
          }
          catch (Exception e) {
            diagnostic.report(Message.create(instrumenter, Message.Kind.ERROR, e.getMessage(), generatedFile));
            break;
          }
        }
        if (changes && !diagnostic.hasErrors()) {
          storageManager.getOutputBuilder().putEntry(generatedFile, content);
        }
      }
    }
  }

  public void saveBuildState(
    BuildContext context, NodeSourceSnapshotDelta srcSnapshotDelta, Iterable<NodeSource> modifiedLibraries, Iterable<NodeSource> deletedLibraries
  ) {

    if (srcSnapshotDelta != null) {
      if (context.hasErrors()) {
        ConfigurationState pastState = ConfigurationState.loadSavedState(context);
        new ConfigurationState(context.getPathMapper(), srcSnapshotDelta.asSnapshot(), pastState.getLibraries()).save(context);
      }
      else {
        new ConfigurationState(context.getPathMapper(), srcSnapshotDelta.asSnapshot(), context.getBinaryDependencies()).save(context);
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
        context.report(Message.create(null, e));
      }
    }
  }

  private static boolean shouldRecompileAll(NodeSourceSnapshotDelta srcSnapshotDelta) {
    return srcSnapshotDelta.isRecompileAll() || srcSnapshotDelta.getChangedPercent() > RECOMPILE_CHANGED_RATIO_PERCENT;
  }

  private static void cleanOutputsForCompiledFiles(BuildContext context, NodeSourceSnapshotDelta snapshotDelta, DependencyGraph depGraph, Iterable<CompilerRunner> compilers, ZipOutputBuilder outBuilder) {
    // separately logging deleted outputs for 'deleted' and 'modified' sources to adjust for existing test data
    
    BooleanFunction<@NotNull NodeSource> isCompilableFilter =
      src -> find(compilers, compiler -> compiler.canCompile(src)) != null;
    
    Collection<String> cleanedOutputsOfDeletedSources = deleteCompilerOutputs(
      depGraph, filter(snapshotDelta.getDeleted(), isCompilableFilter), outBuilder, new ArrayList<>()
    );
    logDeletedPaths(context, cleanedOutputsOfDeletedSources);

    Collection<String> cleanedOutputsOfModifiedSources = deleteCompilerOutputs(
      depGraph, filter(snapshotDelta.getModified(), isCompilableFilter), outBuilder, new ArrayList<>()
    );
    if (!cleanedOutputsOfDeletedSources.isEmpty() || !cleanedOutputsOfModifiedSources.isEmpty()) {
      // delete additional paths only if there are any changes in the output caused by changes in sources
      for (String toDelete : flat(map(compilers, CompilerRunner::getOutputPathsToDelete))) {
        if (outBuilder.deleteEntry(toDelete)) {
          cleanedOutputsOfModifiedSources.add(toDelete);
        }
      }
    }
    logDeletedPaths(context, cleanedOutputsOfModifiedSources);
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
      delta.associate(pair.node, pair.sources);
    }
    return delta;
  }

}
