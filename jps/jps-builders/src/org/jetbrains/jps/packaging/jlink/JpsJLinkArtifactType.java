// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.packaging.jlink;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

public final class JpsJLinkArtifactType extends JpsElementTypeBase<JpsJLinkProperties> implements JpsArtifactType<JpsJLinkProperties> {
  public static final JpsJLinkArtifactType INSTANCE = new JpsJLinkArtifactType();

  private JpsJLinkArtifactType() {
  }
}
