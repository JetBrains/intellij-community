/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ExtensionsSupport;
import org.jetbrains.jps.builders.java.JavaCompilingTool;

import javax.tools.JavaCompiler;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-Nov-16
 */
public abstract class JavaCompilerToolExtension {
  @NotNull
  public abstract String getId();

  /**
   * This method is called before compiler task execution.
   * The extension can install all necessary compiler listeners here.
   *
   * @param compilingTool descriptor of compiler implementation that will perform compilation
   * @param task an instance of compiler task that is going to be executed
   * @param options compiler command line options for this compiler invocation
   * @param diagnosticConsumer diagnostic data collector. Use this object to pass messages and collected data
   */
  public void beforeCompileTaskExecution(@NotNull JavaCompilingTool compilingTool, @NotNull JavaCompiler.CompilationTask task, @NotNull Collection<String> options, @NotNull DiagnosticOutputConsumer diagnosticConsumer) {
  }

  /**
   * Custom data passed through DiagnosticOutputConsumer object with pluginId == thisExtensionId will be passed to this method
   * @param dataName
   * @param content
   */
  public void processData(String dataName, byte[] content) {
  }

  public List<String> getExternalBuildProcessOptions(@NotNull JavaCompilingTool compilingTool) {
    return Collections.emptyList();
  }

  private static final ExtensionsSupport<JavaCompilerToolExtension> ourExtSupport = new ExtensionsSupport<JavaCompilerToolExtension>(JavaCompilerToolExtension.class);
  @NotNull
  public static Collection<JavaCompilerToolExtension> getExtensions() {
    return ourExtSupport.getExtensions();
  }

  @Nullable
  public static JavaCompilerToolExtension getExtension(@NotNull String id) {
    for (JavaCompilerToolExtension extension : getExtensions()) {
      if (id.equals(extension.getId())) {
        return extension;
      }
    }
    return null;
  }
}
