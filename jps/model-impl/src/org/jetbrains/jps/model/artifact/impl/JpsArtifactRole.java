// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsNamedElementCollectionRole;

final class JpsArtifactRole extends JpsElementChildRoleBase<JpsArtifact> {
  private static final JpsArtifactRole INSTANCE = new JpsArtifactRole();
  public static final JpsNamedElementCollectionRole<JpsArtifact> ARTIFACT_COLLECTION_ROLE = JpsNamedElementCollectionRole.create(INSTANCE);

  private JpsArtifactRole() {
    super("artifact");
  }
}
