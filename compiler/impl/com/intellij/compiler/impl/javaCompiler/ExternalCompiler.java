package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class ExternalCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("com.intellij.compiler.impl.javaCompiler.ExternalCompiler");

  @NotNull
  public abstract String[] createStartupCommand(ModuleChunk chunk, CompileContext context, String outputPath)
    throws IOException, IllegalArgumentException;

  @NotNull
  public Process launchProcess(final ModuleChunk chunk, final String outputDir, final CompileContext compileContext) throws IOException {
    final String[] commands = createStartupCommand(chunk, compileContext, outputDir);

    @NonNls final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append("Running compiler: ");
      for (final String command : commands) {
        buf.append(" ").append(command);
      }

      LOG.info(buf.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }

    return Runtime.getRuntime().exec(commands);
  }
}
