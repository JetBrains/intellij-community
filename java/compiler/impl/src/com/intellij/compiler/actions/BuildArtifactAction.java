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
package com.intellij.compiler.actions;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;

import java.util.List;

/**
 * @author nik
 */
public class BuildArtifactAction extends BuildArtifactActionBase {
  public BuildArtifactAction() {
    super("Build");
  }

  @Override
  protected String getDescription() {
    return "Selected artifacts will be built with all dependencies";
  }

  @Override
  protected void performAction(Project project, final List<Artifact> artifacts) {
    CompilerManager.getInstance(project).make(ArtifactCompileScope.createArtifactsScope(project, artifacts), null);
  }
}
