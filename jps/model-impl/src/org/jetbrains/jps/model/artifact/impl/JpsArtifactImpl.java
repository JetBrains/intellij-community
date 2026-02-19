// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase;

import java.util.Objects;

class JpsArtifactImpl<P extends JpsElement> extends JpsNamedCompositeElementBase<JpsArtifactImpl<P>> implements JpsArtifact {
  private static final JpsElementChildRole<JpsCompositePackagingElement>
    ROOT_ELEMENT_CHILD_ROLE = JpsElementChildRoleBase.create("root element");
  private final JpsArtifactType<P> myArtifactType;
  private String myOutputPath;
  private boolean myBuildOnMake;


  JpsArtifactImpl(@NotNull String name, @NotNull JpsCompositePackagingElement rootElement, @NotNull JpsArtifactType<P> type, @NotNull P properties) {
    super(name);
    myArtifactType = type;
    myContainer.setChild(ROOT_ELEMENT_CHILD_ROLE, rootElement);
    myContainer.setChild(type.getPropertiesRole(), properties);
  }

  private JpsArtifactImpl(JpsArtifactImpl<P> original) {
    super(original);
    myArtifactType = original.myArtifactType;
    myOutputPath = original.myOutputPath;
  }

  @Override
  public @NotNull JpsArtifactImpl<P> createCopy() {
    return new JpsArtifactImpl<>(this);
  }

  @Override
  public String getOutputPath() {
    return myOutputPath;
  }

  @Override
  public void setOutputPath(@Nullable String outputPath) {
    if (!Objects.equals(myOutputPath, outputPath)) {
      myOutputPath = outputPath;
    }
  }

  @Override
  public @Nullable String getOutputFilePath() {
    if (Strings.isEmpty(myOutputPath)) {
      return null;
    }

    JpsCompositePackagingElement root = getRootElement();
    return root instanceof JpsArchivePackagingElement ? myOutputPath + "/" + ((JpsArchivePackagingElement)root).getArchiveName() : myOutputPath;
  }

  @Override
  public @NotNull JpsArtifactType<P> getArtifactType() {
    return myArtifactType;
  }

  @Override
  public @NotNull JpsArtifactReferenceImpl createReference() {
    return new JpsArtifactReferenceImpl(getName());
  }

  @Override
  public @NotNull JpsCompositePackagingElement getRootElement() {
    return myContainer.getChild(ROOT_ELEMENT_CHILD_ROLE);
  }

  @Override
  public void setRootElement(@NotNull JpsCompositePackagingElement rootElement) {
    myContainer.setChild(ROOT_ELEMENT_CHILD_ROLE, rootElement);
  }

  @Override
  public P getProperties() {
    return myContainer.getChild(myArtifactType.getPropertiesRole());
  }

  @Override
  public boolean isBuildOnMake() {
    return myBuildOnMake;
  }

  @Override
  public void setBuildOnMake(boolean buildOnMake) {
    if (myBuildOnMake != buildOnMake) {
      myBuildOnMake = buildOnMake;
    }
  }
}
