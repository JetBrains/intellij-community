// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.CompilerOptions;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.util.Collections;
import java.util.Set;

public class JavacCompiler implements BackendCompiler {
  private final Project myProject;

  public JavacCompiler(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull @NonNls String getId() { // used for externalization
    return JavaCompilers.JAVAC_ID;
  }

  @Override
  public @NotNull String getPresentableName() {
    return JavaCompilerBundle.message("compiler.javac.name");
  }

  @Override
  public @NotNull Configurable createConfigurable() {
    return new JavacConfigurable(myProject, JavacConfiguration.getOptions(myProject, JavacConfiguration.class));
  }

  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Collections.singleton(JavaFileType.INSTANCE);
  }

  @Override
  public @NotNull CompilerOptions getOptions() {
    return JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
  }
}
