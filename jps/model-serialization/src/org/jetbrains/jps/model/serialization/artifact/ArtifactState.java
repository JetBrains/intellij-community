// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.model.serialization.SerializationConstants;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
@Tag("artifact")
public class ArtifactState {
  public static final @NonNls String NAME_ATTRIBUTE = "name";
  private @NlsSafe String myName;
  private String myOutputPath;
  private String myArtifactType = "plain";
  private boolean myBuildOnMake;
  private Element myRootElement;
  private List<ArtifactPropertiesState> myPropertiesList = new ArrayList<>();
  private String myExternalSystemId;
  private String myExternalSystemIdInInternalStorage;

  @Attribute(NAME_ATTRIBUTE)
  public @NlsSafe String getName() {
    return myName;
  }

  @Attribute("type")
  public String getArtifactType() {
    return myArtifactType;
  }

  @Attribute("build-on-make")
  public boolean isBuildOnMake() {
    return myBuildOnMake;
  }

  @Attribute(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE)
  public String getExternalSystemId() {
    return myExternalSystemId;
  }

  @Attribute(SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE)
  public String getExternalSystemIdInInternalStorage() {
    return myExternalSystemIdInInternalStorage;
  }

  @Tag("output-path")
  public String getOutputPath() {
    return myOutputPath;
  }

  @Tag("root")
  public Element getRootElement() {
    return myRootElement;
  }

  @Property(surroundWithTag = false)
  @XCollection
  public List<ArtifactPropertiesState> getPropertiesList() {
    return myPropertiesList;
  }

  public void setPropertiesList(List<ArtifactPropertiesState> propertiesList) {
    myPropertiesList = propertiesList;
  }

  public void setArtifactType(String artifactType) {
    myArtifactType = artifactType;
  }

  public void setName(@NlsSafe String name) {
    myName = name;
  }

  public void setOutputPath(String outputPath) {
    myOutputPath = outputPath;
  }

  public void setBuildOnMake(boolean buildOnMake) {
    myBuildOnMake = buildOnMake;
  }

  public void setExternalSystemId(String externalSystemId) {
    myExternalSystemId = externalSystemId;
  }

  public void setExternalSystemIdInInternalStorage(String externalSystemIdInInternalStorage) {
    myExternalSystemIdInInternalStorage = externalSystemIdInInternalStorage;
  }

  public void setRootElement(Element rootElement) {
    myRootElement = rootElement;
  }
}
