package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompileContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.io.*;

public abstract class ExternalCompiler implements BackendCompiler {
  @NotNull
  public abstract String[] createStartupCommand(ModuleChunk chunk, CompileContext context, String outputPath)
    throws IOException, IllegalArgumentException;

  @NotNull
  public Process launchProcess(final ModuleChunk chunk, final String outputDir, final CompileContext compileContext) throws IOException {
    final String[] commands = createStartupCommand(chunk, compileContext, outputDir);

    @NonNls final StringBuffer buf = new StringBuffer(16 * commands.length);
    buf.append("Running compiler: ");
    for (final String command : commands) {
      buf.append(" ").append(command);
    }

    final Process process = Runtime.getRuntime().exec(commands);
    return process;
  }
}
