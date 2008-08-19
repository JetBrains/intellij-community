package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public interface BackendCompiler {
  @NotNull @NonNls String getId(); // used for externalization
  @NotNull String getPresentableName();
  @NotNull Configurable createConfigurable();
  @NotNull
  Set<FileType> getCompilableFileTypes();
  @Nullable OutputParser createErrorParser(final String outputDir);
  @Nullable OutputParser createOutputParser(final String outputDir);

  boolean checkCompiler(final CompileScope scope);

  @NotNull Process launchProcess(
    final ModuleChunk chunk,
    final String outputDir,
    final CompileContext compileContext) throws IOException;

  void compileFinished();
}