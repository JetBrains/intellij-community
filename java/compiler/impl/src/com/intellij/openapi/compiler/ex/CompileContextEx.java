/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.ex;

import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface CompileContextEx extends CompileContext {
  DependencyCache getDependencyCache();

  @Nullable
  VirtualFile getSourceFileByOutputFile(VirtualFile outputFile);

  void addMessage(CompilerMessage message);

  @NotNull
  Set<VirtualFile> getTestOutputDirectories();
  
  /**
   * the same as FileIndex.isInTestSourceContent(), but takes into account generated output dirs
   */
  boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir);

  boolean isInSourceContent(@NotNull VirtualFile fileOrDir);

  void addScope(CompileScope additionalScope);

  long getStartCompilationStamp();

  void recalculateOutputDirs();

  void markGenerated(Collection<VirtualFile> files);

  boolean isGenerated(VirtualFile file);

  void assignModule(@NotNull VirtualFile root, @NotNull Module module, boolean isTestSource, @Nullable com.intellij.openapi.compiler.Compiler compiler);
}
