// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Threads(4)
@Warmup(iterations = 0) // This code affects users since the beginning of the application. Users don't warm up our products.
public class MultiRoutingFileSystemBackendBenchmark {
  @State(Scope.Benchmark)
  public static class BenchmarkState {
    MultiRoutingFileSystemProvider myProvider = new MultiRoutingFileSystemProvider(FileSystems.getDefault().provider());
    MultiRoutingFileSystem myFileSystem = myProvider.getFileSystem(URI.create("file:/"));

    public BenchmarkState() {
      MultiRoutingFileSystemProvider.computeBackend(
        myProvider,
        "\\\\wsl.localhost\\Ubuntu-22.04\\",
        false,
        false,
        (ignored1, ignored2) -> Mockito.mock(FileSystem.class)
      );
    }
  }

  @Benchmark
  public void positiveMatchMatchWithoutPrefixAndWithRegularSlashes(BenchmarkState state) {
    state.myFileSystem.getBackend("//wsl.localhost/Ubuntu-22.04/");
  }

  @Benchmark
  public void positiveMatchMatchWithoutPrefixAndWithRegularSlashesAndLongPath(BenchmarkState state) {
    state.myFileSystem.getBackend("//wsl.localhost/Ubuntu-22.04/home/user/IntellijProjects/intellij/community/out/classes/production/com/intellij/openapi/util/io/NioFilesTest.class");
  }

  @Benchmark
  public void positiveMatchMatchWithoutPrefixAndWithBackslashes(BenchmarkState state) {
    state.myFileSystem.getBackend("\\\\wsl.localhost\\Ubuntu-22.04\\");
  }

  @Benchmark
  public void positiveMatchMatchWithoutPrefixAndWithBackslashesAndLongPath(BenchmarkState state) {
    state.myFileSystem.getBackend("\\\\wsl.localhost\\Ubuntu-22.04\\home\\user\\IntellijProjects\\intellij\\community\\out\\classes\\production\\com\\intellij\\openapi\\util\\io\\NioFilesTest.class");
  }

  @Benchmark
  public void negativeMatchMatchWithoutPrefixAndWithBackslashes(BenchmarkState state) {
    state.myFileSystem.getBackend("C:\\");
  }
}