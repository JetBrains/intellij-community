package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.ElementSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.util.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.filter;

public interface AsyncLibraryGraphLoader {

  interface GraphState {
    void cancel();
    @NotNull Pair<NodeSourceSnapshot, Graph> get() throws Exception;
  }

  interface GraphStateChange {
    void cancel();
    @NotNull Pair<NodeSourceSnapshot, Graph> getPast() throws Exception;
    @NotNull Pair<NodeSourceSnapshot, Graph> getPresent() throws Exception;
  }

  static List<GraphStateChange> submit(BuildContext context, Iterable<NodeSource> libs, ElementSnapshot<NodeSource> pastSnapshot, ElementSnapshot<NodeSource> presentSnapshot) {
    try (GraphLoaderExecutor executor = GraphLoaderExecutor.create()) {
      List<GraphStateChange> tasks = new ArrayList<>();
      for (NodeSource presentLib : libs) {
        Path presentLibPath = context.getPathMapper().toPath(presentLib);
        Path pastLibPath = DataPaths.getJarBackupStoreFile(context, presentLibPath);
        CompletableFuture<Pair<NodeSourceSnapshot, Graph>> presentGraph = submitGraphLoadTask(presentLib, presentSnapshot.getDigest(presentLib), presentLibPath, executor);
        CompletableFuture<Pair<NodeSourceSnapshot, Graph>> pastGraph = submitGraphLoadTask(presentLib, pastSnapshot.getDigest(presentLib), pastLibPath, executor);
        tasks.add(new GraphStateChange() {
          @Override
          public void cancel() {
            presentGraph.cancel(true);
            pastGraph.cancel(true);
          }

          @Override
          public @NotNull Pair<NodeSourceSnapshot, Graph> getPast() throws Exception {
            return pastGraph.get();
          }

          @Override
          public @NotNull Pair<NodeSourceSnapshot, Graph> getPresent() throws Exception {
            return presentGraph.get();
          }
        });
      }
      return tasks;
    }
  }

  static List<GraphState> submit(ElementSnapshot<NodeSource> librariesSnapshot, Predicate<NodeSource> libFilter, Function<NodeSource, Path> toLoadPath) {
    try (GraphLoaderExecutor executor = GraphLoaderExecutor.create()) {
      List<GraphState> tasks = new ArrayList<>();
      for (NodeSource lib : filter(librariesSnapshot.getElements(), libFilter)) {
        CompletableFuture<Pair<NodeSourceSnapshot, Graph>> result = submitGraphLoadTask(lib, librariesSnapshot.getDigest(lib), toLoadPath.apply(lib), executor);
        tasks.add(new GraphState() {
          @Override
          public void cancel() {
            result.cancel(true);
          }

          @Override
          public @NotNull Pair<NodeSourceSnapshot, Graph> get() throws Exception {
            return result.get();
          }
        });
      }
      return tasks;
    }
  }

  private static CompletableFuture<Pair<NodeSourceSnapshot, Graph>> submitGraphLoadTask(NodeSource lib, @NotNull String digest, @NotNull Path loadPath, Executor executor) {
    return CompletableFuture.supplyAsync(() -> LibraryGraphLoader.getLibraryGraph(lib, digest, loadPath), executor);
  }
}
