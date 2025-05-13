// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.javac.ast.JavacReferenceCollector;
import org.jetbrains.jps.javac.ast.api.JavacFileData;

import javax.tools.*;

public abstract class AbstractRefCollectorCompilerToolExtension extends JavaCompilerToolExtension {
  @Override
  public final void beforeCompileTaskExecution(@NotNull JavaCompilingTool compilingTool,
                                               @NotNull JavaCompiler.CompilationTask task,
                                               @NotNull Iterable<String> options,
                                               @NotNull final DiagnosticOutputConsumer diagnosticConsumer) {
    if (compilingTool.isCompilerTreeAPISupported() && isEnabled()) {
      JavacReferenceCollector.installOn(task, new JavacReferenceCollector.Consumer<JavacFileData>() {
        @Override
        public void consume(JavacFileData data) {
          diagnosticConsumer.registerJavacFileData(data);
        }
      });
    }
  }

  protected abstract boolean isEnabled();
}
