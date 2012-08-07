package org.jetbrains.jps.model.artifact.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.impl.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataRole;

/**
 * @author nik
 */
public class JpsArtifactImpl extends JpsNamedCompositeElementBase<JpsArtifactImpl> implements JpsArtifact {
  private static final JpsElementChildRole<JpsCompositePackagingElement>
    ROOT_ELEMENT_CHILD_ROLE = JpsElementChildRoleBase.create("root element");
  private static final JpsTypedDataRole<JpsArtifactType> TYPED_DATA_ROLE = new JpsTypedDataRole<JpsArtifactType>();
  private String myOutputPath;
  private boolean myBuildOnMake;


  public JpsArtifactImpl(@NotNull String name, @NotNull JpsCompositePackagingElement rootElement, @NotNull JpsArtifactType type) {
    super(name);
    myContainer.setChild(ROOT_ELEMENT_CHILD_ROLE, rootElement);
    myContainer.setChild(TYPED_DATA_ROLE, new JpsTypedDataImpl<JpsArtifactType>(type, DummyJpsElementProperties.INSTANCE));
  }

  private JpsArtifactImpl(JpsArtifactImpl original) {
    super(original);
    myOutputPath = original.myOutputPath;
  }

  @NotNull
  @Override
  public JpsArtifactImpl createCopy() {
    return new JpsArtifactImpl(this);
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public void setOutputPath(@Nullable String outputPath) {
    if (!Comparing.equal(myOutputPath, outputPath)) {
      myOutputPath = outputPath;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsArtifactType getArtifactType() {
    return myContainer.getChild(TYPED_DATA_ROLE).getType();
  }

  @NotNull
  @Override
  public JpsArtifactReferenceImpl createReference() {
    return new JpsArtifactReferenceImpl(getName());
  }

  @NotNull
  @Override
  public JpsCompositePackagingElement getRootElement() {
    return myContainer.getChild(ROOT_ELEMENT_CHILD_ROLE);
  }

  @Override
  public void setRootElement(@NotNull JpsCompositePackagingElement rootElement) {
    myContainer.setChild(ROOT_ELEMENT_CHILD_ROLE, rootElement);
  }

  @Override
  public boolean isBuildOnMake() {
    return myBuildOnMake;
  }

  @Override
  public void setBuildOnMake(boolean buildOnMake) {
    if (myBuildOnMake != buildOnMake) {
      myBuildOnMake = buildOnMake;
      fireElementChanged();
    }
  }
}
