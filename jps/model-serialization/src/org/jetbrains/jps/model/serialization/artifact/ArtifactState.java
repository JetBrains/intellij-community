/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization.artifact;

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
  private String myArtifactType = "plain";
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
