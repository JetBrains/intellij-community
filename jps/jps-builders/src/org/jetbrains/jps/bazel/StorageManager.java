// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.impl.AbiJarBuilder;
import org.jetbrains.jps.bazel.impl.ZipOutputBuilderImpl;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.PersistentMVStoreMapletFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jetbrains.jps.javac.Iterators.collect;
import static org.jetbrains.jps.javac.Iterators.isEmpty;

public class StorageManager implements Closeable {
  private final BuildContext myContext;
  private GraphConfiguration myGraphConfig;
  private ZipOutputBuilderImpl myOutputBuilder;
  private AbiJarBuilder myAbiOutputBuilder;

  public StorageManager(BuildContext context) {
    myContext = context;
  }

  public void cleanBuildState() throws IOException {
    close();
    Path output = myContext.getOutputZip();
    Path abiOutput = myContext.getAbiOutputZip();
    Path srcSnapshot = DataPaths.getConfigStateStoreFile(myContext);

    BuildProcessLogger logger = myContext.getBuildLogger();
    if (logger.isEnabled() && !myContext.isRebuild()) {
      // need this for tests
      Set<String> deleted = new HashSet<>();
      try (var out = new ZipOutputBuilderImpl(output)) {
        collect(out.getEntryNames(), deleted);
      }
      if (!isEmpty(deleted)) {
        logger.logDeletedPaths(deleted);
      }
    }

    Files.deleteIfExists(output);
    if (abiOutput != null) {
      Files.deleteIfExists(abiOutput);
    }
    Files.deleteIfExists(srcSnapshot);
    Files.deleteIfExists(DataPaths.getDepGraphStoreFile(myContext));

    cleanDependenciesBackupDir(myContext);
    Files.deleteIfExists(DataPaths.getDependenciesBackupStoreDir(myContext));
  }

  public static void cleanDependenciesBackupDir(BuildContext context) throws IOException {
    Path oldLibrariesDir = DataPaths.getDependenciesBackupStoreDir(context);
    if (Files.exists(oldLibrariesDir)) {
      try (var jars = Files.list(oldLibrariesDir)) {
        for (Path jar : jars.collect(Collectors.toList())) {
          Files.deleteIfExists(jar);
        }
      }
    }
  }

  @NotNull
  public GraphConfiguration getGraphConfiguration() throws IOException {
    GraphConfiguration config = myGraphConfig;
    if (config == null) {
      DependencyGraphImpl graph = new DependencyGraphImpl(
        new PersistentMVStoreMapletFactory(DataPaths.getDepGraphStoreFile(myContext).toString(), Math.min(8, Runtime.getRuntime().availableProcessors()))
      );
      myGraphConfig = config = GraphConfiguration.create(graph, myContext.getPathMapper());
    }
    return config;
  }

  @NotNull
  public ZipOutputBuilderImpl getOutputBuilder() throws IOException {
    ZipOutputBuilderImpl builder = myOutputBuilder;
    if (builder == null) {
      myOutputBuilder = builder = new ZipOutputBuilderImpl(myContext.getOutputZip());
    }
    return builder;
  }

  @Nullable
  public AbiJarBuilder getAbiOutputBuilder() throws IOException {
    AbiJarBuilder builder = myAbiOutputBuilder;
    if (builder == null) {
      Path abiOutputPath = myContext.getAbiOutputZip();
      if (abiOutputPath != null) {
        myAbiOutputBuilder = builder = new AbiJarBuilder(abiOutputPath);
      }
    }
    return builder;
  }

  public DependencyGraph getGraph() throws IOException {
    return getGraphConfiguration().getGraph();
  }

  @Override
  public void close() {
    GraphConfiguration config = myGraphConfig;
    if (config != null) {
      myGraphConfig = null;
      safeClose(config.getGraph());
    }

    safeClose(myOutputBuilder);
    myOutputBuilder = null;

    safeClose(myAbiOutputBuilder);
    myAbiOutputBuilder = null;
  }

  private void safeClose(Closeable cl) {
    try {
      if (cl != null) {
        cl.close();
      }
    }
    catch (Throwable e) {
      myContext.report(Message.create(null, e));
    }
  }

}
