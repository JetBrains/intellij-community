/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsArtifactService {

  public static JpsArtifactService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsArtifactService.class);
  }

  public abstract <P extends JpsElement> JpsArtifact createArtifact(@NotNull String name, @NotNull JpsCompositePackagingElement rootElement,
                                                                    @NotNull JpsArtifactType<P> type, @NotNull P properties);

  public abstract List<JpsArtifact> getArtifacts(@NotNull JpsProject project);

  public abstract List<JpsArtifact> getSortedArtifacts(@NotNull JpsProject project);

  public abstract <P extends JpsElement> JpsArtifact addArtifact(@NotNull JpsProject project, @NotNull String name,
                                                                 @NotNull JpsCompositePackagingElement rootElement,
                                          @NotNull JpsArtifactType<P> type, @NotNull P properties);

  public abstract JpsArtifactReference createReference(@NotNull String artifactName);
}
