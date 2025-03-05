// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactRootElement;

class JpsArtifactRootElementImpl extends JpsCompositePackagingElementBase<JpsArtifactRootElementImpl> implements JpsArtifactRootElement {
  JpsArtifactRootElementImpl() {
  }

  private JpsArtifactRootElementImpl(JpsArtifactRootElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsArtifactRootElementImpl createCopy() {
    return new JpsArtifactRootElementImpl();
  }

  @Override
  public @NotNull JpsArtifactRootElementImpl createElementCopy() {
    return new JpsArtifactRootElementImpl();
  }
}
