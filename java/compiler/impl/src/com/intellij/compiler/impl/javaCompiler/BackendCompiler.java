// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompilableFileTypesProvider;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.CompilerOptions;

import java.util.Set;

/**
 * Extension point that is mostly used to represent options for java compiler in settings.
 * <p>
 * This extension point is used on the IDE side only, it must be accompanied by an implementation of 
 * {@link org.jetbrains.jps.builders.java.JavaCompilingTool} in the JPS build process.
 */
public interface BackendCompiler {
  ProjectExtensionPointName<BackendCompiler> EP_NAME = new ProjectExtensionPointName<>("com.intellij.java.compiler");

  CompilerOptions EMPTY_OPTIONS = new CompilerOptions() { };

  /**
   * Used for externalization.
   * @return unique identifier for a given compiler.
   */
  @NotNull
  String getId();

  /**
   * @return string which will be shown in (Preferences | Build, Execution, Deployment | Compiler | Java Compiler).
   */
  @NlsContexts.ListItem
  @NotNull String getPresentableName();

  /**
   * @return configurable which will be shown under the dropdown in (Preferences | Build, Execution, Deployment | Compiler | Java Compiler).
   */
  @NotNull
  Configurable createConfigurable();

  /**
   * @return set of files which will be processed by the compiler.
   *
   * If you need to handle other files during the build see {@link CompilableFileTypesProvider}
   */
  @NotNull
  Set<FileType> getCompilableFileTypes();

  /**
   * Set of options for a given compiler.
   * It will be used to store checkboxes state for (Preferences | Build, Execution, Deployment | Compiler) tab.
   * Consider inheriting {@link org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions}, most of the
   * places expect it as an implementation.
   */
  @NotNull
  default CompilerOptions getOptions() {
    return EMPTY_OPTIONS;
  }
}