// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactRootElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.ex.JpsComplexPackagingElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class JpsArtifactOutputPackagingElementImpl extends JpsComplexPackagingElementBase<JpsArtifactOutputPackagingElementImpl>
  implements JpsArtifactOutputPackagingElement {
  private static final JpsElementChildRole<JpsArtifactReference>
    ARTIFACT_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("artifact reference");

  JpsArtifactOutputPackagingElementImpl(@NotNull JpsArtifactReference reference) {
    myContainer.setChild(ARTIFACT_REFERENCE_CHILD_ROLE, reference);
  }

  private JpsArtifactOutputPackagingElementImpl(JpsArtifactOutputPackagingElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsArtifactOutputPackagingElementImpl createElementCopy() {
    return new JpsArtifactOutputPackagingElementImpl(this);
  }

  @Override
  public @NotNull JpsArtifactReference getArtifactReference() {
    return myContainer.getChild(ARTIFACT_REFERENCE_CHILD_ROLE);
  }

  @Override
  public List<JpsPackagingElement> getSubstitution() {
    JpsArtifact artifact = getArtifactReference().resolve();
    if (artifact == null) return Collections.emptyList();
    JpsCompositePackagingElement rootElement = artifact.getRootElement();
    if (rootElement instanceof JpsArtifactRootElement) {
      return new ArrayList<>(rootElement.getChildren());
    }
    else {
      return Collections.singletonList(rootElement);
    }
  }
}
