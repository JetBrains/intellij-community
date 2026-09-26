package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.IndexFactory;
import org.jetbrains.jps.dependency.kotlin.KotlinSubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.LookupsIndex;

import java.io.IOException;

public class MVStoreGraphConfiguration implements GraphConfiguration {
  private final PersistentMVStoreMapletFactory myContainerFactory;
  private final DependencyGraph graph;
  private final BuildContext myContext;

  public MVStoreGraphConfiguration(BuildContext context) throws IOException {
    this.myContext = context;
    String storePath = DataPaths.getDepGraphStoreFile(context).toString();
    int maxBuilderThreads = Math.min(8, Runtime.getRuntime().availableProcessors());
    myContainerFactory = new PersistentMVStoreMapletFactory(storePath, maxBuilderThreads);
    boolean kotlinCriEnabled = context.getKotlinCriStoragePath() != null;
    if (kotlinCriEnabled) {
      graph = new DependencyGraphImpl(myContainerFactory, IndexFactory.create(LookupsIndex::new, KotlinSubclassesIndex::new));
    }
    else {
      graph = new DependencyGraphImpl(myContainerFactory);
    }
  }

  @Override
  public @NotNull NodeSourcePathMapper getPathMapper() {
    return myContext.getPathMapper();
  }

  @Override
  public @NotNull DependencyGraph getGraph() {
    return graph;
  }

  @Override
  public boolean isGraphUpdated() {
    return myContainerFactory.hasUpdates();
  }
}
