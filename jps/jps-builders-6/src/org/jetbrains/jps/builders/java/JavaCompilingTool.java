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

import javax.tools.*;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an implementation which will be used to compile *.java files. By default, {@link org.jetbrains.jps.builders.impl.java.JavacCompilerTool javac}
 * is used. A user may choose a different variant from extensions implementing {@link com.intellij.compiler.impl.javaCompiler.BackendCompiler}
 * on the IDE side.
 * <p>
 * Implementations of this class are registered as Java services, by creating a file META-INF/services/org.jetbrains.jps.builders.java.JavaCompilingTool
 * containing the qualified name of your implementation class.
 */
public abstract class JavaCompilingTool {
  /**
   * Returns the ID of this tool, it must match to {@link com.intellij.compiler.impl.javaCompiler.BackendCompiler#getId()}.
   */
  @NotNull
  public abstract String getId();

  @Nullable
  public String getAlternativeId() {
    return null;
  }

  public boolean isCompilerTreeAPISupported() {
    return false;
  }

  @NotNull
  public abstract String getDescription();

  @NotNull
  public abstract JavaCompiler createCompiler() throws CannotCreateJavaCompilerException;

  @NotNull
  public abstract List<File> getAdditionalClasspath();

  public List<String> getDefaultCompilerOptions() {
    return Collections.emptyList();
  }

  /**
   * Override this method to modify the options list before they are passed to {@link JavaFileManager#handleOption(String, Iterator)}.
   */
  public void preprocessOptions(List<String> options) {
  }
}
