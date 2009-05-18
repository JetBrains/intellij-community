package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
@Tag("artifact")
public class ArtifactState {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  private String myName;
  private String myOutputPath;
  private String myArtifactType = PlainArtifactType.ID;
  private boolean myBuildOnMake;
  private Element myRootElement;
  private List<ArtifactPropertiesState> myPropertiesList = new ArrayList<ArtifactPropertiesState>();

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

  @Tag("output-path")
  public String getOutputPath() {
    return myOutputPath;
  }

  @Tag("root")
  public Element getRootElement() {
    return myRootElement;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
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

  public void setRootElement(Element rootElement) {
    myRootElement = rootElement;
  }
}
