package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactRootElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.impl.JpsElementKindBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsArtifactOutputPackagingElementImpl extends JpsComplexPackagingElementBase<JpsArtifactOutputPackagingElementImpl>
  implements JpsArtifactOutputPackagingElement {
  private static final JpsElementKind<JpsArtifactReference> ARTIFACT_REFERENCE_KIND = new JpsElementKindBase<JpsArtifactReference>("artifact reference");

  public JpsArtifactOutputPackagingElementImpl(@NotNull JpsArtifactReference reference) {
    myContainer.setChild(ARTIFACT_REFERENCE_KIND, reference);
  }

  private JpsArtifactOutputPackagingElementImpl(JpsArtifactOutputPackagingElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsArtifactOutputPackagingElementImpl createCopy() {
    return new JpsArtifactOutputPackagingElementImpl(this);
  }

  @Override
  @NotNull
  public JpsArtifactReference getArtifactReference() {
    return myContainer.getChild(ARTIFACT_REFERENCE_KIND);
  }

  @Override
  public List<JpsPackagingElement> getSubstitution() {
    JpsArtifact artifact = getArtifactReference().resolve();
    if (artifact == null) return Collections.emptyList();
    JpsCompositePackagingElement rootElement = artifact.getRootElement();
    if (rootElement instanceof JpsArtifactRootElement) {
      return new ArrayList<JpsPackagingElement>(rootElement.getChildren());
    }
    else {
      return Collections.<JpsPackagingElement>singletonList(rootElement);
    }
  }
}
