/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.compiler;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyCompileContext implements CompileContext {
  protected DummyCompileContext() {
  }

  private static final DummyCompileContext OUR_INSTANCE = new DummyCompileContext();

  public static DummyCompileContext getInstance() {
    return OUR_INSTANCE;
  }

  public Project getProject() {
    return null;
  }

  public void addMessage(@NotNull CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
  }


  public void addMessage(@NotNull CompilerMessageCategory category,
                         String message,
                         @Nullable String url,
                         int lineNum,
                         int columnNum,
                         Navigatable navigatable) {
  }

  @NotNull
  public CompilerMessage[] getMessages(@NotNull CompilerMessageCategory category) {
    return CompilerMessage.EMPTY_ARRAY;
  }

  public int getMessageCount(CompilerMessageCategory category) {
    return 0;
  }

  @NotNull
  public ProgressIndicator getProgressIndicator() {
    return null;
  }

  public CompileScope getCompileScope() {
    return null;
  }

  public CompileScope getProjectCompileScope() {
    return null;
  }

  public void requestRebuildNextTime(String message) {
  }

  public boolean isRebuildRequested() {
    return false;
  }

  @Nullable
  public String getRebuildReason() {
    return null;
  }

  public Module getModuleByFile(@NotNull VirtualFile file) {
    return null;
  }

  public boolean isAnnotationProcessorsEnabled() {
    return false;
  }

  public VirtualFile getModuleOutputDirectory(@NotNull final Module module) {
    return ReadAction.compute(() -> CompilerModuleExtension.getInstance(module).getCompilerOutputPath());
  }

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

  public boolean isMake() {
    return false; // stub implementation
  }

  @Override
  public boolean isAutomake() {
    return false;
  }

  public boolean isRebuild() {
    return false;
  }
}
