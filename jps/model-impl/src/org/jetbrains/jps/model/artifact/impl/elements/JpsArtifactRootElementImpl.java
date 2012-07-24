package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactRootElement;

/**
 * @author nik
 */
public class JpsArtifactRootElementImpl extends JpsCompositePackagingElementBase<JpsArtifactRootElementImpl> implements JpsArtifactRootElement {
  public JpsArtifactRootElementImpl() {
  }

  private JpsArtifactRootElementImpl(JpsArtifactRootElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsArtifactRootElementImpl createCopy() {
    return new JpsArtifactRootElementImpl();
  }
}
