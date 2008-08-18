package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public abstract class ExternalCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("com.intellij.compiler.impl.javaCompiler.ExternalCompiler");

  @NotNull
  public abstract String[] createStartupCommand(ModuleChunk chunk, CompileContext context, String outputPath)
    throws IOException, IllegalArgumentException;

  @NotNull
  public Collection<? extends FileType> getCompilableFileTypes() {
    return Arrays.asList(StdFileTypes.JAVA);
  }

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
