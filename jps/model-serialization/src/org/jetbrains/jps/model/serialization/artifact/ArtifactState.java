/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@Tag("artifact")
public class ArtifactState {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  private String myName;
  private String myOutputPath;
  private String myArtifactType = "plain";
  private boolean myBuildOnMake;
  private Element myRootElement;
  private List<ArtifactPropertiesState> myPropertiesList = new ArrayList<>();
  private String myExternalSystemId;

  @Attribute(NAME_ATTRIBUTE)
  public String getName() {
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

  @Attribute("external-system-id")
  public String getExternalSystemId() {
    return myExternalSystemId;
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

  public void setName(String name) {
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

  public void setRootElement(Element rootElement) {
    myRootElement = rootElement;
  }
}
