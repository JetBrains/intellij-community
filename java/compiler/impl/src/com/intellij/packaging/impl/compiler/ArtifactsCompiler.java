/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author nik
 */
public class ArtifactsCompiler extends GenericCompiler<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState> {
  static final Key<Set<String>> WRITTEN_PATHS_KEY = Key.create("artifacts_written_paths");
  static final Key<Set<Artifact>> AFFECTED_ARTIFACTS = Key.create("affected_artifacts");

  public ArtifactsCompiler() {
    super("artifacts_compiler", 0, GenericCompiler.CompileOrderPlace.PACKAGING);
  }

  @Nullable
  public static ArtifactsCompiler getInstance(@NotNull Project project) {
    final ArtifactsCompiler[] compilers = CompilerManager.getInstance(project).getCompilers(ArtifactsCompiler.class);
    return compilers.length == 1 ? compilers[0] : null;
  }

  static void setAffectedArtifacts(final CompileContext context, Set<Artifact> artifacts) {
    context.putUserData(AFFECTED_ARTIFACTS, artifacts);
  }

  static void addWrittenPaths(final CompileContext context, Set<String> writtenPaths) {
    Set<String> paths = context.getUserData(WRITTEN_PATHS_KEY);
    if (paths == null) {
      paths = new THashSet<String>();
      context.putUserData(WRITTEN_PATHS_KEY, paths);
    }
    paths.addAll(writtenPaths);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getItemKeyDescriptor() {
    return STRING_KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<VirtualFilePersistentState> getSourceStateExternalizer() {
    return VirtualFilePersistentState.EXTERNALIZER;
  }

  @NotNull
  @Override
  public DataExternalizer<ArtifactPackagingItemOutputState> getOutputStateExternalizer() {
    return new ArtifactPackagingItemExternalizer();
  }

  @NotNull
  @Override
  public GenericCompilerInstance<ArtifactBuildTarget, ? extends CompileItem<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState>, String, VirtualFilePersistentState, ArtifactPackagingItemOutputState> createInstance(
    @NotNull CompileContext context) {
    return new ArtifactsCompilerInstance(context);
  }

  @NotNull
  public String getDescription() {
    return "Artifacts Packaging Compiler";
  }

  @Nullable
  public static Set<Artifact> getAffectedArtifacts(final CompileContext compileContext) {
    return compileContext.getUserData(AFFECTED_ARTIFACTS);
  }

  @Nullable
  public static Set<String> getWrittenPaths(@NotNull CompileContext context) {
    return context.getUserData(WRITTEN_PATHS_KEY);
  }
}
