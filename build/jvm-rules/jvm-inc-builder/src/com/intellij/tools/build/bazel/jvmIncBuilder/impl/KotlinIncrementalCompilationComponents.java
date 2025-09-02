// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler;
import org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope;
import org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.ProjectFileSearchScopeProvider;
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents;
import org.jetbrains.kotlin.modules.TargetId;

import java.util.Set;

public class KotlinIncrementalCompilationComponents implements IncrementalCompilationComponents, ProjectFileSearchScopeProvider {
  private final String moduleName;
  private final IncrementalCache cache;
  private final VirtualFile myOutputRoot;

  public KotlinIncrementalCompilationComponents(String moduleName, IncrementalCache cache, @NotNull VirtualFile outputRoot) {
    this.moduleName = moduleName;
    this.cache = cache;
    myOutputRoot = outputRoot;
  }

  @Override
  public @NotNull AbstractProjectFileSearchScope createSearchScope(@NotNull VfsBasedProjectEnvironment pe) {
    return new PsiBasedProjectFileSearchScope(new KotlinToJVMBytecodeCompiler.DirectoriesScope(pe.getProject(), Set.of(myOutputRoot)));
  }

  @Override
  public @NotNull IncrementalCache getIncrementalCache(@NotNull TargetId targetId) {
    if (!targetId.getName().equals(moduleName)) throw new RuntimeException("Incremental cache for target " + moduleName + " not found");
    return cache;
  }
}