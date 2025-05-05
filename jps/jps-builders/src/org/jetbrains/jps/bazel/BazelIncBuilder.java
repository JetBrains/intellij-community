// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.impl.*;
import org.jetbrains.jps.bazel.runner.BytecodeInstrumenter;
import org.jetbrains.jps.bazel.runner.CompilerRunner;
import org.jetbrains.jps.bazel.runner.RunnerFactory;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.impl.PersistentMVStoreMapletFactory;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

public class BazelIncBuilder {

  private static final List<RunnerFactory<? extends CompilerRunner>> ourCompilers = List.of(
    ResourcesCopy::new
  );
  private static final List<RunnerFactory<? extends CompilerRunner>> ourRoundCompilers = List.of(
    KotlinCompilerRunner::new, JavaCompilerRunner::new
  );
  private static final List<RunnerFactory<? extends BytecodeInstrumenter>> ourInstrumenters = List.of(
    NotNullInstrumenter::new, FormsInstrumenter::new
  );

  private static GraphConfiguration setupGraphConfiguration(BuildContext context) throws IOException {
    DependencyGraphImpl graph = new DependencyGraphImpl(
      new PersistentMVStoreMapletFactory(DataPaths.getDepGraphStoreFile(context).toString(), Math.min(8, Runtime.getRuntime().availableProcessors()))
    );
    return GraphConfiguration.create(graph, context.getPathMapper());
  }

  public ExitCode build(BuildContext context) {
    // todo: support cancellation checks
    // todo: additional diagnostics, if necessary

    GraphUpdater graphUpdater = new GraphUpdater(context.getTargetName());
    DiagnosticSink diagnostic = context;
    ZipOutputBuilder outputBuilder = null;
    NodeSourceSnapshotDelta srcSnapshotDelta = null;
    Iterable<NodeSource> modifiedLibraries = List.of();
    DependencyGraph depGraph = null;
    ConfigurationState presentState = new ConfigurationState(context.getSources(), context.getBinaryDependencies());
    try {
      if (context.isRebuild()) {
        srcSnapshotDelta = new SnapshotDeltaImpl(context.getSources());
        srcSnapshotDelta.markRecompileAll(); // force rebuild
      }
      else {
        ConfigurationState pastState = ConfigurationState.loadSavedState(context);
        srcSnapshotDelta = new SnapshotDeltaImpl(pastState.getSources(), context.getSources());
        if (!srcSnapshotDelta.isRecompileAll() && pastState.getClasspathStructureDigest() != presentState.getClasspathStructureDigest()) {
          srcSnapshotDelta.markRecompileAll();
        }
        if (!srcSnapshotDelta.isRecompileAll()) {
          Predicate<NodeSource> abiJarFilter = ns -> DataPaths.isAbiJar(ns.toString());
          modifiedLibraries = new ElementSnapshotDeltaImpl<>(
            ElementSnapshot.derive(pastState.getLibraries(), abiJarFilter),
            ElementSnapshot.derive(context.getBinaryDependencies(), abiJarFilter)
          ).getModified();
        }
      }

      List<Graph> libGraphs = new ArrayList<>();
      Delta libDelta = null;
      if (!srcSnapshotDelta.isRecompileAll()) {
        // todo: create library graphs
        for (Path lib : map(modifiedLibraries, context.getPathMapper()::toPath)) {
          Path pastJarStoreFile = DataPaths.getPastJarStoreFile(context, lib);
        }
      }

      if (srcSnapshotDelta.isRecompileAll()) {
        cleanBuildState(context);
      }

      GraphConfiguration graphConfig = setupGraphConfiguration(context);
      depGraph = graphConfig.getGraph();

      if (!srcSnapshotDelta.isRecompileAll()) {
        //if (libDelta != null) {
        //  // todo: update lib dependencies
        //  //srcSnapshotDelta = graphUpdater.updateDependencyGraph(depGraph, srcSnapshotDelta, libDelta, /*errorsDetected: */ false);
        //}

        // expand compile scope
        Delta sourceOnlyDelta = depGraph.createDelta(srcSnapshotDelta.getModified(), srcSnapshotDelta.getDeleted(), true);
        srcSnapshotDelta = graphUpdater.updateDependencyGraph(depGraph, srcSnapshotDelta, sourceOnlyDelta, /*errorsDetected: */ false);
      }

      outputBuilder = new ZipOutputBuilderImpl(context.getOutputZip());
      List<CompilerRunner> compilers = collect(map(ourCompilers, f -> f.create(context)), new ArrayList<>());
      List<CompilerRunner> roundCompilers = collect(map(ourRoundCompilers, f -> f.create(context)), new ArrayList<>());
      List<BytecodeInstrumenter> instrumenters = collect(map(ourInstrumenters, f -> f.create(context)), new ArrayList<>());

      boolean isInitialRound = true;
      do {
        diagnostic = isInitialRound? new PostponedDiagnosticSink() : context; // for initial round postpone error reporting
        OutputSinkImpl outSink = new OutputSinkImpl(diagnostic, outputBuilder, instrumenters);

        if (isInitialRound) {
          List<String> deletedPaths = new ArrayList<>();
          for (NodeSource source : filter(flat(srcSnapshotDelta.getDeleted(), srcSnapshotDelta.getModified()), s -> find(compilers, compiler -> compiler.canCompile(s)) != null)) {
            // source paths are assumed to be relative to source roots, so under the output root the directory structure is the same
            String path = source.toString();
            if (outputBuilder.deleteEntry(path)) {
              deletedPaths.add(path);
            }
          }

          logDeletedPaths(context, deletedPaths);

          for (CompilerRunner runner : compilers) {
            List<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
            if (toCompile.isEmpty()) {
              continue;
            }

            runner.compile(toCompile, diagnostic, outSink);

            if (diagnostic.hasErrors()) {
              break;
            }
          }
        }

        if (!diagnostic.hasErrors()) {
          // delete outputs corresponding to deleted or recompiled sources
          cleanCompiledFiles(context, srcSnapshotDelta, depGraph, outputBuilder);

          for (CompilerRunner runner : roundCompilers) {
            List<NodeSource> toCompile = collect(filter(srcSnapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
            if (toCompile.isEmpty()) {
              continue;
            }
            ExitCode code = runner.compile(toCompile, diagnostic, outSink);
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

        NodeSourceSnapshotDelta nextSnapshotDelta = graphUpdater.updateDependencyGraph(depGraph, srcSnapshotDelta, createGraphDelta(depGraph, srcSnapshotDelta, outSink), diagnostic.hasErrors());

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

      return ExitCode.OK;
    }
    catch (Throwable e) {
      diagnostic.report(Message.create(null, e));
      return ExitCode.ERROR;
    }
    finally {
      if (diagnostic instanceof PostponedDiagnosticSink) {
        // report postponed errors, if necessary
        ((PostponedDiagnosticSink)diagnostic).drainTo(context);
      }
      if (srcSnapshotDelta != null) {
        new ConfigurationState(srcSnapshotDelta.asSnapshot(), presentState.getLibraries()).save(context);
      }
      // todo: save abi-jar
      safeClose(outputBuilder, diagnostic);
      safeClose(depGraph, diagnostic);
    }
  }

  private static void cleanBuildState(BuildContext context) throws IOException {
    Path output = context.getOutputZip();
    Path abiOutput = context.getAbiOutputZip();
    Path srcSnapshot = DataPaths.getConfigStateStoreFile(context);

    BuildProcessLogger logger = context.getBuildLogger();
    if (logger.isEnabled() && !context.isRebuild()) {
      // need this for tests
      Set<String> deleted = new HashSet<>();
      for (Path outPath : abiOutput != null? List.of(output, abiOutput) : List.of(output)) {
        try (var out = new ZipOutputBuilderImpl(outPath)) {
          collect(out.getEntryNames(), deleted);
        }
      }
      logDeletedPaths(context, deleted);
    }
    
    Files.deleteIfExists(output);
    if (abiOutput != null) {
      Files.deleteIfExists(abiOutput);
    }
    Files.deleteIfExists(srcSnapshot);
    Files.deleteIfExists(DataPaths.getDepGraphStoreFile(context));
  }

  private static void cleanCompiledFiles(BuildContext context, NodeSourceSnapshotDelta snapshotDelta, DependencyGraph depGraph, ZipOutputBuilder outputBuilder) {
    for (Iterable<@NotNull NodeSource> sourceGroup : List.of(snapshotDelta.getDeleted(), snapshotDelta.getModified())) {
      if (isEmpty(sourceGroup)) {
        continue;
      }
      // separately logging deleted outputs for 'deleted' and 'modified' sources to adjust for existing test data
      List<String> deletedPaths = new ArrayList<>();
      for (Node<?, ?> node : filter(flat(map(sourceGroup, depGraph::getNodes)), n -> n instanceof JVMClassNode)) {
        String outputPath = ((JVMClassNode<?, ?>) node).getOutFilePath();
        if (outputBuilder.deleteEntry(outputPath)) {
          deletedPaths.add(outputPath);
        }
      }
      logDeletedPaths(context, deletedPaths);
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
    for (Pair<Node<?, ?>, Iterable<NodeSource>> pair : outSink.getNodes()) {
      delta.associate(pair.getFirst(), pair.getSecond());
    }
    return delta;
  }


  private static void safeClose(Closeable cl, DiagnosticSink diagnostic) {
    if (cl == null) {
      return;
    }
    try {
      cl.close();
    }
    catch (Throwable e) {
      diagnostic.report(Message.create(null, e));
    }
  }

  private static NodeSourceSnapshotDelta createLibraryNodeSourceSnapshotDelta(Path pastJar, Path presentJar) throws IOException {
    try (var pastBuilder = new AbiJarBuilder(pastJar)) {
      try (var presentBuilder = new AbiJarBuilder(presentJar)) {
        return new SnapshotDeltaImpl(createLibraryNodeSourceSnapshot(pastBuilder), createLibraryNodeSourceSnapshot(presentBuilder));
      }
    }
  }

  private static NodeSourceSnapshot createLibraryNodeSourceSnapshot(AbiJarBuilder builder) {
    Map<NodeSource, String> result = new HashMap<>();
    for (Map.Entry<String, Long> entry : builder.getPackageIndex().entrySet()) {
      result.put(new PathSource(entry.getKey()), Long.toHexString(entry.getValue()));
    }
    return new SourceSnapshotImpl(result);
  }

  private static Graph loadReadonlyLibraryGraph(DependencyGraph depGraph, ZipOutputBuilder builder) {
    return loadReadonlyLibraryGraph(depGraph.createDelta(Set.of(), Set.of(), false), ClassDataZipEntry.fromZipOutputBuilder(builder));
  }

  private static Graph loadReadonlyLibraryGraph(DependencyGraph depGraph, Path jarPath) throws IOException {
    try (var is = Files.newInputStream(jarPath)) {
      return loadReadonlyLibraryGraph(depGraph.createDelta(Set.of(), Set.of(), false), ClassDataZipEntry.fromSteam(is));
    }
  }

  private static Graph loadReadonlyLibraryGraph(Delta delta, Iterator<ClassDataZipEntry> entries) {
    // for this presentation we use packages as 'node sources', and class files in the corresponding package as 'nodes'
    Map<String, Iterable<NodeSource>> sourcesMap = new HashMap<>();
    while (entries.hasNext()) {
      ClassDataZipEntry entry = entries.next();
      String path = entry.getPath();
      String parent = entry.getParent();
      if (parent != null) {
        delta.associate(
          JvmClassNodeBuilder.createForLibrary(path, entry.getClassReader()).getResult(),
          sourcesMap.computeIfAbsent(parent, n -> Set.of(new PathSource(n)))
        );
      }
    }
    return delta;
  }

}
