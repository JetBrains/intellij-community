package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.IndexFactory;
import org.jetbrains.jps.dependency.impl.SnapshotGraph;
import org.jetbrains.jps.dependency.kotlin.KotlinSubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.LookupsIndex;

import java.io.IOException;

public class SnapshotGraphConfiguration implements GraphConfiguration {
  public static final String STORE_FILE_NAME = "dep-graph.dat";
  private final BuildContext myContext;
  private final SnapshotGraph myGraph;

  public SnapshotGraphConfiguration(BuildContext context) throws IOException {
    myContext = context;
    boolean kotlinCriEnabled = context.getKotlinCriStoragePath() != null;
    myGraph = new SnapshotGraph(
      DataPaths.getDepGraphStoreFile(context).resolveSibling(STORE_FILE_NAME), kotlinCriEnabled? IndexFactory.create(LookupsIndex::new, KotlinSubclassesIndex::new) : IndexFactory.mandatoryIndices()
    );
  }

  @Override
  public @NotNull NodeSourcePathMapper getPathMapper() {
    return myContext.getPathMapper();
  }

  @Override
  public @NotNull DependencyGraph getGraph() {
    return myGraph;
  }

  @Override
  public boolean isGraphUpdated() {
    return myGraph.getIntegratesCount() > 0;
  }
}
