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
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.jps.incremental.artifacts.impl.JpsBuilderArtifactServiceImpl;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class JpsBuilderArtifactService {
  private static final JpsBuilderArtifactService ourInstance = new JpsBuilderArtifactServiceImpl();

  public static JpsBuilderArtifactService getInstance() {
    return ourInstance;
  }

  public abstract Collection<JpsArtifact> getArtifacts(JpsModel model, boolean includeSynthetic);

  public abstract Collection<JpsArtifact> getSyntheticArtifacts(JpsModel model);
}
