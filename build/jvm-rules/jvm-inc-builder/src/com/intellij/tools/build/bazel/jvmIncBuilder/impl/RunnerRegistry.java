package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation.BytecodeInstrumentationRunner;
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
    new Entry<>(KotlinCompilerRunner.class, KotlinCompilerRunner::new, p -> p.getFileName().toString().endsWith(".kt")),
    new Entry<>(JavaCompilerRunner.class, JavaCompilerRunner::new, p -> p.getFileName().toString().endsWith(".java")),
    new Entry<>(BytecodeInstrumentationRunner.class, BytecodeInstrumentationRunner::new),
    new Entry<>(FormsCompiler.class, FormsCompiler::new, p -> p.getFileName().toString().endsWith(FormBinding.FORM_EXTENSION))
  );

  public static Iterable<RunnerFactory<? extends CompilerRunner>> getRoundCompilers() {
    return filter(map(ourRunners, entry -> CompilerRunner.class.isAssignableFrom(entry.runnerClass())? (RunnerFactory<CompilerRunner>)entry.factory : null), Objects::nonNull);
  }

  public static boolean isCompilableSource(Path path) {
    for (Entry<?> entry : ourRunners) {
      if (entry.supportedSources.test(path)) {
        return true;
      }
    }
    return false;
  }

  private record Entry<R extends Runner> (Class<R> runnerClass, RunnerFactory<R> factory, Predicate<? super Path> supportedSources) {
    Entry(Class<R> runnerClass, RunnerFactory<R> factory) {
      this(runnerClass, factory, path -> false);
    }
  }
}
