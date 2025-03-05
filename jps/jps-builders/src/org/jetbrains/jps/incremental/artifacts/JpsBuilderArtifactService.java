// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.incremental.artifacts.impl.JpsBuilderArtifactServiceImpl;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.Collection;

public abstract class JpsBuilderArtifactService {
  private static final JpsBuilderArtifactService ourInstance = new JpsBuilderArtifactServiceImpl();

  public static JpsBuilderArtifactService getInstance() {
    return ourInstance;
  }

  public abstract @Unmodifiable Collection<JpsArtifact> getArtifacts(JpsModel model, boolean includeSynthetic);

  public abstract Collection<JpsArtifact> getSyntheticArtifacts(JpsModel model);
}
