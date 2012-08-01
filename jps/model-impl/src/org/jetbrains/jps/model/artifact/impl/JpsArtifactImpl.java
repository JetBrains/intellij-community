package org.jetbrains.jps.model.artifact.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.impl.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataKind;

/**
 * @author nik
 */
public class JpsArtifactImpl extends JpsNamedCompositeElementBase<JpsArtifactImpl> implements JpsArtifact {
  private static final JpsElementKind<JpsCompositePackagingElement> ROOT_ELEMENT_KIND = JpsElementKindBase.create("root element");
  private static final JpsTypedDataKind<JpsArtifactType> TYPED_DATA_KIND = new JpsTypedDataKind<JpsArtifactType>();
  private String myOutputPath;

  public JpsArtifactImpl(@NotNull String name, @NotNull JpsCompositePackagingElement rootElement, @NotNull JpsArtifactType type) {
    super(name);
    myContainer.setChild(ROOT_ELEMENT_KIND, rootElement);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsArtifactType>(type, DummyJpsElementProperties.INSTANCE));
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
    return myContainer.getChild(TYPED_DATA_KIND).getType();
  }

  @NotNull
  @Override
  public JpsArtifactReferenceImpl createReference() {
    return new JpsArtifactReferenceImpl(getName());
  }

  @NotNull
  @Override
  public JpsCompositePackagingElement getRootElement() {
    return myContainer.getChild(ROOT_ELEMENT_KIND);
  }

  @Override
  public void setRootElement(@NotNull JpsCompositePackagingElement rootElement) {
    myContainer.setChild(ROOT_ELEMENT_KIND, rootElement);
  }
}
