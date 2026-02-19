package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

interface GraphLoaderExecutor extends Executor, AutoCloseable {
  @Override
  void close();

  static GraphLoaderExecutor create() {
    if (Runtime.version().feature() >= 21) { // if VirtualThreads are supported
      //noinspection resource
      ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
      return new GraphLoaderExecutor() {
        @Override
        public void close() {
          // use shutdown in order not to wait until all submitted tasks finish
          virtualExecutor.shutdown();
        }

        @Override
        public void execute(@NotNull Runnable command) {
          virtualExecutor.execute(command);
        }
      };
    }

    // fallback to executor with "caller runs" policy
    return new GraphLoaderExecutor() {
      @Override
      public void close() {
        // empty
      }

      @Override
      public void execute(@NotNull Runnable command) {
        try {
          ForkJoinPool.commonPool().execute(command);
        }
        catch (RejectedExecutionException e) {
          command.run();
        }
      }
    };
  }
}
