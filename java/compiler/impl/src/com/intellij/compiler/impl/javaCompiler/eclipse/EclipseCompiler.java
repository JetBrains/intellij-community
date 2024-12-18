// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.model.java.compiler.CompilerOptions;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.util.Collections;
import java.util.Set;

public final class EclipseCompiler implements BackendCompiler {
  private final Project myProject;

  public EclipseCompiler(Project project) {
    myProject = project;
  }

  public static boolean isInitialized() {
    return EclipseCompilerTool.findEcjJarFile() != null;
  }

  @Override
  public @NotNull String getId() { // used for externalization
    return JavaCompilers.ECLIPSE_ID;
  }

  @Override
  public @NotNull String getPresentableName() {
    return JavaCompilerBundle.message("compiler.eclipse.name");
  }

  @Override
  public @NotNull Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(myProject, EclipseCompilerConfiguration.getOptions(myProject, EclipseCompilerConfiguration.class));
  }

  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Collections.singleton(JavaFileType.INSTANCE);
  }

  @Override
  public @NotNull CompilerOptions getOptions() {
    return EclipseCompilerConfiguration.getOptions(myProject, EclipseCompilerConfiguration.class);
  }
}
