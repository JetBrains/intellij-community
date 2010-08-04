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
package com.intellij.ide.mavenService;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class MavenService {

  public static MavenService getInstance(Project project) {
    return ServiceManager.getService(project, MavenService.class);
  }

  public abstract Artifact createArtifact(String groupId, String artifactId, String versionId);

  public abstract Artifact[] getVersions(String groupId, String artifactId);

  public abstract List<Artifact> resolveDependencies(List<Artifact> artifact, List<String> repositories);

  public abstract List<DownloadResult> downloadArtifacts(List<Artifact> artifacts, int options);
}
