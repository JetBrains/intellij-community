// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter;

import java.util.Collections;
import java.util.List;

import static com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.isCaseSensitiveFS;

final class CompilerReferenceIndexBuildParametersProvider extends BuildProcessParametersProvider {
  private final @NotNull Project project;

  CompilerReferenceIndexBuildParametersProvider(@NotNull Project project) { this.project = project; }

  @Override
  public @NotNull List<String> getVMArguments() {
    boolean enabled = CompilerReferenceServiceBase.isEnabled();
    if (!enabled) return Collections.emptyList();
    boolean caseSensitiveFS = isCaseSensitiveFS(project);
    return List.of("-D" + JavaBackwardReferenceIndexWriter.PROP_KEY + "=true",
                   "-D" + JavaBackwardReferenceIndexWriter.FS_KEY + "=" + Boolean.valueOf(caseSensitiveFS));
  }
}
