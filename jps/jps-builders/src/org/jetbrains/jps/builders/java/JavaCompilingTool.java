/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;

import javax.tools.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class JavaCompilingTool {
  @NotNull
  public abstract String getId();

  @Nullable
  public String getAlternativeId() {
    return null;
  }

  @NotNull
  public abstract String getDescription();

  @NotNull
  public abstract JavaCompiler createCompiler() throws CannotCreateJavaCompilerException;

  @NotNull
  public abstract List<File> getAdditionalClasspath();

  public void processCompilerOptions(@NotNull CompileContext context, @NotNull List<String> options) {
  }

  public void prepareCompilationTask(@NotNull JavaCompiler.CompilationTask task, @NotNull Collection<String> options) {
  }

  public List<String> getDefaultCompilerOptions() {
    return Collections.emptyList();
  }
}
