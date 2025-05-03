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
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.impl.PersistentMVStoreMapletFactory;
import org.jetbrains.jps.dependency.java.JVMClassNode;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.jetbrains.jps.javac.Iterators.*;

public class BazelIncBuilder {
  private static final String SOURCE_SNAPSHOT_FILE_NAME = "src-snapshot.dat";
  private static final String DEP_GRAPH_FILE_NAME = "dep-graph.mv";

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
      new PersistentMVStoreMapletFactory(getDepGraphStoreFile(context).toString(), Math.min(8, Runtime.getRuntime().availableProcessors()))
    );
    return GraphConfiguration.create(graph, context.getPathMapper());
  }

  public ExitCode build(BuildContext context) {
    // todo: support cancellation checks
    // todo: additional diagnostics, if necessary

    GraphUpdater graphUpdater = new GraphUpdater(context.getTargetName());
    DiagnosticSink diagnostic = context;
    ZipOutputBuilder outputBuilder = null;
    SourceSnapshotDelta snapshotDelta = null;
    DependencyGraph depGraph = null;
    ConfigurationState presentState = ConfigurationState.create(context);
    try {
      if (context.isRebuild()) {
        snapshotDelta = new SourceSnapshotDeltaImpl(context.getSources());
        snapshotDelta.markRecompileAll(); // force rebuild
      }
      else {
        ConfigurationState pastState = loadConfigurationState(context);
        snapshotDelta = new SourceSnapshotDeltaImpl(pastState.getSourceSnapshot(), context.getSources());
        if (!snapshotDelta.isRecompileAll() && !pastState.getClasspathStructureDigest().equals(presentState.getClasspathStructureDigest())) {
          snapshotDelta.markRecompileAll();
        }
      }

      if (snapshotDelta.isRecompileAll()) {
        cleanBuildState(context);
      }

      GraphConfiguration graphConfig = setupGraphConfiguration(context);
      depGraph = graphConfig.getGraph();

      if (!snapshotDelta.isRecompileAll()) {
        // todo: process changes in libs

        // expand compile scope
        Delta sourceOnlyDelta = depGraph.createDelta(snapshotDelta.getModified(), snapshotDelta.getDeleted(), true);
        snapshotDelta = graphUpdater.updateDependencyGraph(depGraph, snapshotDelta, sourceOnlyDelta, /*errorsDetected: */ false);
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
          for (NodeSource source : filter(flat(snapshotDelta.getDeleted(), snapshotDelta.getModified()), s -> find(compilers, compiler -> compiler.canCompile(s)) != null)) {
            // source paths are assumed to be relative to source roots, so under the output root the directory structure is the same
            String path = source.toString();
            if (outputBuilder.deleteEntry(path)) {
              deletedPaths.add(path);
            }
          }

          logDeletedPaths(context, deletedPaths);

          for (CompilerRunner runner : compilers) {
            List<NodeSource> toCompile = collect(filter(snapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
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
          cleanCompiledFiles(context, snapshotDelta, depGraph, outputBuilder);

          for (CompilerRunner runner : roundCompilers) {
            List<NodeSource> toCompile = collect(filter(snapshotDelta.getModified(), runner::canCompile), new ArrayList<>());
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

        SourceSnapshotDelta nextSnapshotDelta = graphUpdater.updateDependencyGraph(depGraph, snapshotDelta, createGraphDelta(depGraph, snapshotDelta, outSink), diagnostic.hasErrors());

        if (!diagnostic.hasErrors()) {
          snapshotDelta = nextSnapshotDelta;
        }
        else {
          if (snapshotDelta.isRecompileAll() || !nextSnapshotDelta.hasChanges()) {
            return ExitCode.ERROR;
          }
          // keep previous snapshot delta, just augment it with the newly found sources for recompilation
          if (nextSnapshotDelta.isRecompileAll()) {
            snapshotDelta.markRecompileAll();
          }
          else {
            for (NodeSource source : nextSnapshotDelta.getModified()) {
              snapshotDelta.markRecompile(source);
            }
          }
          if (!isInitialRound) {
            return ExitCode.ERROR;
          }
          // for initial round, partial compilation and when analysis has expanded the scope, attempt automatic error recovery by repeating the compilation with the expanded scope
        }

        isInitialRound = false;
      }
      while (snapshotDelta.hasChanges());

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
      if (snapshotDelta != null) {
        saveConfigurationState(context, presentState.derive(snapshotDelta.asSnapshot()));
      }
      // todo: save abi-jar
      safeClose(outputBuilder, diagnostic);
      safeClose(depGraph, diagnostic);
    }
  }

  private static void cleanBuildState(BuildContext context) throws IOException {
    Path output = context.getOutputZip();
    Path abiOutput = context.getAbiOutputZip();
    Path srcSnapshot = getSourceSnapshotStoreFile(context);

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
    Files.deleteIfExists(getDepGraphStoreFile(context));
  }

  private static void cleanCompiledFiles(BuildContext context, SourceSnapshotDelta snapshotDelta, DependencyGraph depGraph, ZipOutputBuilder outputBuilder) {
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

  private static Delta createGraphDelta(DependencyGraph depGraph, SourceSnapshotDelta snapshotDelta, OutputSinkImpl outSink) {
    Delta delta = depGraph.createDelta(snapshotDelta.getModified(), snapshotDelta.getDeleted(), false);
    for (Pair<Node<?, ?>, Iterable<NodeSource>> pair : outSink.getNodes()) {
      delta.associate(pair.getFirst(), pair.getSecond());
    }
    return delta;
  }


  private static void saveConfigurationState(BuildContext context, ConfigurationState state) {
    Path snapshotPath = getSourceSnapshotStoreFile(context);
    try (var stream = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(snapshotPath), new Deflater(Deflater.BEST_SPEED)))) {
      stream.writeUTF(state.getClasspathStructureDigest());
      state.getSourceSnapshot().write(new GraphDataOutputImpl(stream));
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
    }
  }

  private static ConfigurationState loadConfigurationState(BuildContext context) {
    Path oldSnapshot = getSourceSnapshotStoreFile(context);
    try (var stream = new DataInputStream(new InflaterInputStream(Files.newInputStream(oldSnapshot, StandardOpenOption.READ)))) {
      String depsDigest = stream.readUTF();
      return ConfigurationState.create(new SourceSnapshotImpl(stream, PathSource::new), depsDigest);
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
      return ConfigurationState.EMPTY;
    }
  }

  private static @NotNull Path getSourceSnapshotStoreFile(BuildContext context) {
    return context.getDataDir().resolve(SOURCE_SNAPSHOT_FILE_NAME);
  }

  private static @NotNull Path getDepGraphStoreFile(BuildContext context) {
    return context.getDataDir().resolve(DEP_GRAPH_FILE_NAME);
  }

  private static boolean isAbiJar(Path path) {
    return path.toString().endsWith("-abi.jar"); // todo: better criterion?
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

  private interface ConfigurationState {
    ConfigurationState EMPTY = create(SourceSnapshot.EMPTY, "");
    
    SourceSnapshot getSourceSnapshot();

    // tracks names and order of classpath entries as well as content digests of all third-party dependencies
    String getClasspathStructureDigest();

    default ConfigurationState derive(SourceSnapshot snapshot) {
      return create(snapshot, getClasspathStructureDigest());
    }

    static ConfigurationState create(SourceSnapshot snapshot, String depsDigest) {
      return new ConfigurationState() {
        @Override
        public SourceSnapshot getSourceSnapshot() {
          return snapshot;
        }

        @Override
        public String getClasspathStructureDigest() {
          return depsDigest;
        }
      };
    }

    static ConfigurationState create(BuildContext context) {
      PathSnapshot deps = context.getBinaryDependencies();

      // digest name, count and order of classpath entries as well as content digests of all non-abi deps
      Function<@NotNull Path, Iterable<String>> digestMapper =
        path -> isAbiJar(path)? List.of(path.toString()) : List.of(path.toString(), deps.getDigest(path));
      
      return create(context.getSources(), Utils.digest(flat(map(deps.getElements(), digestMapper))));
    }
  }
}
