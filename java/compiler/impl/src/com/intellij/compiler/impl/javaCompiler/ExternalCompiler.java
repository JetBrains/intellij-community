/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public abstract class ExternalCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.ExternalCompiler");
  private static final Set<FileType> COMPILABLE_TYPES = Collections.<FileType>singleton(StdFileTypes.JAVA);

  @NotNull
  public abstract String[] createStartupCommand(ModuleChunk chunk, CompileContext context, String outputPath)
    throws IOException, IllegalArgumentException;

  @NotNull
  public Set<FileType> getCompilableFileTypes() {
    return COMPILABLE_TYPES;
  }

  /**
   * Checks if the compiler can compile the specified file.
   *
   * @param file    the file to check.
   * @param context the context for the current compile operation.
   * @return true if can compile the file, false otherwise. If the method returns false, <code>file</code>
   *         will not be included in the list of files passed to {@link #compile(CompileContext, com.intellij.util.Chunk < com.intellij.openapi.module.Module >,com.intellij.openapi.vfs.VirtualFile[], com.intellij.openapi.compiler.TranslatingCompiler.OutputSink)}.
   */
  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return getCompilableFileTypes().contains(file.getFileType());
  }

  @NotNull
  public Process launchProcess(@NotNull final ModuleChunk chunk, @NotNull final String outputDir, @NotNull final CompileContext compileContext) throws IOException {
    final String[] commands = createStartupCommand(chunk, compileContext, outputDir);

    if (LOG.isDebugEnabled()) {
      @NonNls final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        buf.append("\n===================================Environment:===========================\n");
        for (String pair : EnvironmentUtil.getEnvironment()) {
          buf.append("\t").append(pair).append("\n");
        }
        buf.append("=============================================================================\n");
        buf.append("Running compiler: ");
        for (final String command : commands) {
          buf.append(" ").append(command);
        }

        LOG.debug(buf.toString());
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }

    return Runtime.getRuntime().exec(commands);
  }
}
