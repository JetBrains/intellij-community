// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class DummyCompileContext implements CompileContext {
  private final Project myProject;

  protected DummyCompileContext(Project project) {
    myProject = project;
  }

  public static @NotNull DummyCompileContext create(@NotNull Project project) {
    return new DummyCompileContext(project);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void addMessage(@NotNull CompilerMessageCategory category,
                         @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                         @Nullable String url, int lineNum, int columnNum, @Nullable Navigatable navigatable,
                         Collection<String> moduleNames) {
  }

  @Override
  public CompilerMessage @NotNull [] getMessages(@NotNull CompilerMessageCategory category) {
    return CompilerMessage.EMPTY_ARRAY;
  }

  @Override
  public int getMessageCount(CompilerMessageCategory category) {
    return 0;
  }

  @Override
  public @NotNull ProgressIndicator getProgressIndicator() {
    return DumbProgressIndicator.INSTANCE;
  }

  @Override
  public CompileScope getCompileScope() {
    return null;
  }

  @Override
  public CompileScope getProjectCompileScope() {
    return null;
  }

  @Override
  public void requestRebuildNextTime(String message) {
  }

  @Override
  public boolean isRebuildRequested() {
    return false;
  }

  @Override
  public @Nullable String getRebuildReason() {
    return null;
  }

  @Override
  public Module getModuleByFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public boolean isAnnotationProcessorsEnabled() {
    return false;
  }

  @Override
  public VirtualFile getModuleOutputDirectory(final @NotNull Module module) {
    return ReadAction.compute(() -> CompilerModuleExtension.getInstance(module).getCompilerOutputPath());
  }

  @Override
  public VirtualFile getModuleOutputDirectoryForTests(Module module) {
    return null;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
  }

  @Override
  public boolean isMake() {
    return false; // stub implementation
  }

  @Override
  public boolean isAutomake() {
    return false;
  }

  @Override
  public boolean isRebuild() {
    return false;
  }
}
