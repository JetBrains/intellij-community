package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.runner.BytecodeInstrumenter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.Runner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.RunnerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.map;

public final class RunnerRegistry {
  private static final List<Entry<?>> ourRunners = List.of(
    new Entry<>(KotlinCompilerRunner.class, KotlinCompilerRunner::new, true, p -> p.getFileName().toString().endsWith(".kt")),
    new Entry<>(JavaCompilerRunner.class, JavaCompilerRunner::new, true, p -> p.getFileName().toString().endsWith(".java")),
    new Entry<>(NotNullInstrumenter.class, NotNullInstrumenter::new),
    new Entry<>(FormsInstrumenter.class, FormsInstrumenter::new)
  );

  public static Iterable<RunnerFactory<? extends CompilerRunner>> getCompilers() {
    return filter(map(ourRunners, entry -> !entry.isRoundCompiler && CompilerRunner.class.isAssignableFrom(entry.runnerClass())? (RunnerFactory<CompilerRunner>)entry.factory : null), Objects::nonNull);
  }

  public static Iterable<RunnerFactory<? extends CompilerRunner>> getRoundCompilers() {
    return filter(map(ourRunners, entry -> entry.isRoundCompiler && CompilerRunner.class.isAssignableFrom(entry.runnerClass())? (RunnerFactory<CompilerRunner>)entry.factory : null), Objects::nonNull);
  }

  public static Iterable<RunnerFactory<? extends BytecodeInstrumenter>> getIntrumenters() {
    return filter(map(ourRunners, entry -> !entry.isRoundCompiler && BytecodeInstrumenter.class.isAssignableFrom(entry.runnerClass())? (RunnerFactory<BytecodeInstrumenter>)entry.factory : null), Objects::nonNull);
  }

  public static boolean isCompilableSource(Path path) {
    for (Entry<?> entry : filter(ourRunners, e -> CompilerRunner.class.isAssignableFrom(e.runnerClass()))) {
      if (entry.supportedSources.test(path)) {
        return true;
      }
    }
    return false;
  }

  private record Entry<R extends Runner> (Class<R> runnerClass, RunnerFactory<R> factory, boolean isRoundCompiler, Predicate<? super Path> supportedSources) {
    Entry(Class<R> runnerClass, RunnerFactory<R> factory) {
      this(runnerClass, factory, false, path -> false);
    }
  }
}
