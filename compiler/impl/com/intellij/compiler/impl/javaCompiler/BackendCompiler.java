package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.compiler.CompileContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

interface BackendCompiler {
  @Nullable OutputParser createErrorParser(final String outputDir);
  @Nullable OutputParser createOutputParser(final String outputDir);

  boolean checkCompiler();

  @NotNull Process launchProcess(
    final ModuleChunk chunk,
    final String outputDir,
    final CompileContext compileContext) throws IOException;

  void processTerminated();
}