package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public interface BackendCompiler {
  @NotNull @NonNls String getId(); // used for externalization
  @NotNull String getPresentableName();
  @NotNull Configurable createConfigurable();
  @Nullable OutputParser createErrorParser(final String outputDir);
  @Nullable OutputParser createOutputParser(final String outputDir);

  boolean checkCompiler();

  @NotNull Process launchProcess(
    final ModuleChunk chunk,
    final String outputDir,
    final CompileContext compileContext) throws IOException;

  void compileFinished();
}