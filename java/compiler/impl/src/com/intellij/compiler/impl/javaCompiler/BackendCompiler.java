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

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public interface BackendCompiler {
  ExtensionPointName<BackendCompiler> EP_NAME = ExtensionPointName.create("com.intellij.java.compiler");

  @NotNull @NonNls String getId(); // used for externalization
  @NotNull String getPresentableName();
  @NotNull Configurable createConfigurable();
  @NotNull Set<FileType> getCompilableFileTypes();
  @Nullable OutputParser createErrorParser(@NotNull String outputDir, Process process);
  @Nullable OutputParser createOutputParser(@NotNull String outputDir);

  boolean checkCompiler(final CompileScope scope);

  @NotNull Process launchProcess(
    @NotNull ModuleChunk chunk,
    @NotNull String outputDir,
    @NotNull CompileContext compileContext) throws IOException;

  void compileFinished();

}
