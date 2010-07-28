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
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.AdditionalCompileScopeProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author nik
 */
public class ArtifactAdditionalCompileScopeProvider extends AdditionalCompileScopeProvider {
  @Override
  public CompileScope getAdditionalScope(@NotNull final CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull final Project project) {
    if (ArtifactCompileScope.getArtifacts(baseScope) != null) {
      return null;
    }
    final ArtifactsCompiler compiler = ArtifactsCompiler.getInstance(project);
    if (compiler == null || !filter.acceptCompiler(compiler)) {
      return null;
    }
    return new ReadAction<CompileScope>() {
      protected void run(final Result<CompileScope> result) {
        final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, baseScope);
        result.setResult(ArtifactCompileScope.createScopeForModulesInArtifacts(project, artifacts));
      } 
    }.execute().getResultObject();
  }
}
