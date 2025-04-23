// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import com.intellij.openapi.util.Pair;
import org.jetbrains.jps.bazel.impl.*;
import org.jetbrains.jps.bazel.runner.BytecodeInstrumenter;
import org.jetbrains.jps.bazel.runner.CompilerRunner;
import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.java.JVMClassNode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.jetbrains.jps.javac.Iterators.*;

public class BazelIncBuilder {
  private static final String SOURCE_SNAPSHOT_FILE_NAME = "src-snapshot.dat";

  private static final List<CompilerRunner> ourCompilers = List.of(
    new ResourcesCopy()
  );
  private static final List<CompilerRunner> ourRoundCompilers = List.of(
    new KotlinCompilerRunner(), new JavaCompilerRunner()
  );
  private static final List<BytecodeInstrumenter> ourInstrumenters = List.of(
    new NotNullInstrumenter(), new FormsInstrumenter()
  );

  public ExitCode build(BuildContext context) {
    // todo: support cancellation checks

    SourceSnapshotDelta snapshotDelta;
    if (context.isRebuild()) {
      snapshotDelta = new SnapshotDeltaImpl(context.getSources());
      snapshotDelta.markRecompileAll();
    }
    else {
      snapshotDelta = new SnapshotDeltaImpl(getOldSourceSnapshot(context), context.getSources());
    }

    GraphUpdater graphUpdater = new GraphUpdater(context.getTargetName());
    DiagnosticSink diagnostic = context;
    try {
      if (snapshotDelta.isRecompileAll()) {
        context.cleanBuildState();
      }
      else {
        DependencyGraph depGraph = context.getGraphConfig().getGraph();
        // todo: process changes in libs

        // expand compile scope
        Delta sourceOnlyDelta = depGraph.createDelta(snapshotDelta.getSourcesToRecompile(), snapshotDelta.getDeletedSources(), true);
        snapshotDelta = graphUpdater.updateDependencyGraph(depGraph, snapshotDelta, sourceOnlyDelta, /*errorsDetected: */ false);
      }

      ZipOutputBuilder outputBuilder = new ZipOutputBuilderImpl(context.getOutputZip());
      DependencyGraph depGraph = context.getGraphConfig().getGraph();
      BuilderArgs builderArgs = context.getBuilderArgs();

      boolean isInitialRound = true;
      do {
        diagnostic = isInitialRound? new PostponedDiagnosticSink() : context; // for initial round postpone error reporting
        OutputSinkImpl outSink = new OutputSinkImpl(diagnostic, outputBuilder, ourInstrumenters);

        if (isInitialRound) {
          for (NodeSource source : filter(flat(snapshotDelta.getDeletedSources(), snapshotDelta.getSourcesToRecompile()), s -> find(ourCompilers, compiler -> compiler.canCompile(s)) != null)) {
            // source paths are assumed to be relative to source roots, so under the output root the sirectory structure is the same
            outputBuilder.deleteEntry(source.toString());
          }
          for (CompilerRunner runner : ourCompilers) {
            runner.compile(snapshotDelta.getSourcesToRecompile(), builderArgs, diagnostic, outSink);
            if (diagnostic.hasErrors()) {
              break;
            }
          }
        }

        if (!diagnostic.hasErrors()) {
          // delete outputs corresponding to deleted or recompiled sources
          for (Node<?,?> node : flat(map(flat(snapshotDelta.getDeletedSources(), snapshotDelta.getSourcesToRecompile()), depGraph::getNodes))) {
            if (node instanceof JVMClassNode) {
              outputBuilder.deleteEntry(((JVMClassNode<?, ?>)node).getOutFilePath());
            }
          }
          for (CompilerRunner runner : ourRoundCompilers) {
            ExitCode code = runner.compile(snapshotDelta.getSourcesToRecompile(), builderArgs, diagnostic, outSink);
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
            for (NodeSource source : nextSnapshotDelta.getSourcesToRecompile()) {
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

      // todo: save output jar and abi-jar
      //outputBuilder.write(context.getOutputZip());

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
      saveSourceSnapshot(context, snapshotDelta.asSnapshot());
      // todo: close graph and save all caches
    }
  }

  private static Delta createGraphDelta(DependencyGraph depGraph, SourceSnapshotDelta snapshotDelta, OutputSinkImpl outSink) {
    Delta delta = depGraph.createDelta(snapshotDelta.getSourcesToRecompile(), snapshotDelta.getDeletedSources(), false);
    for (Pair<Node<?, ?>, Iterable<NodeSource>> pair : outSink.getNodes()) {
      delta.associate(pair.getFirst(), pair.getSecond());
    }
    return delta;
  }

  private static void saveSourceSnapshot(BuildContext context, SourceSnapshot snapshot) {
    Path snapshotPath = context.getBaseDir().resolve(SOURCE_SNAPSHOT_FILE_NAME);
    try (var stream = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(snapshotPath), new Deflater(Deflater.BEST_SPEED)))) {
      snapshot.write(new GraphDataOutputImpl(stream));
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
    }
  }

  private static SourceSnapshot getOldSourceSnapshot(BuildContext context) {
    Path oldSnapshot = context.getBaseDir().resolve(SOURCE_SNAPSHOT_FILE_NAME);
    try (var stream = new DataInputStream(new InflaterInputStream(Files.newInputStream(oldSnapshot, StandardOpenOption.READ)))) {
      return new SourceSnapshotImpl(stream, PathSource::new);
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
      return SourceSnapshot.EMPTY;
    }
  }
}
