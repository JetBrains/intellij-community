// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ExtensionsSupport;
import org.jetbrains.jps.builders.java.JavaCompilingTool;

import javax.tools.*;
import java.util.Collection;

public abstract class JavaCompilerToolExtension {
   /**
   * This method is called before compiler task execution.
   * The extension can install all necessary compiler listeners here.
   *  @param compilingTool descriptor of compiler implementation that will perform compilation
   * @param task an instance of compiler task that is going to be executed
    * @param options compiler command line options for this compiler invocation
    * @param diagnosticConsumer diagnostic data collector. Use this object to pass messages and collected data
    */
  public void beforeCompileTaskExecution(@NotNull JavaCompilingTool compilingTool, @NotNull JavaCompiler.CompilationTask task, @NotNull Iterable<String> options, @NotNull DiagnosticOutputConsumer diagnosticConsumer) {
  }

  private static final ExtensionsSupport<JavaCompilerToolExtension> ourExtSupport = new ExtensionsSupport<>(JavaCompilerToolExtension.class);
  @NotNull
  public static Collection<JavaCompilerToolExtension> getExtensions() {
    return ourExtSupport.getExtensions();
  }
}
