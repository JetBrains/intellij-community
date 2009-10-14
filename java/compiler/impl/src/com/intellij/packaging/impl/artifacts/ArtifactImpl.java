/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactImpl extends UserDataHolderBase implements ModifiableArtifact {
  private CompositePackagingElement<?> myRootElement;
  private String myName;
  private boolean myBuildOnMake;
  private String myOutputPath;
  private ArtifactType myArtifactType;
  private Map<ArtifactPropertiesProvider, ArtifactProperties<?>> myProperties;

  public ArtifactImpl(@NotNull String name, @NotNull ArtifactType artifactType, boolean buildOnMake, @NotNull CompositePackagingElement<?> rootElement,
                      String outputPath) {
    myName = name;
    myArtifactType = artifactType;
    myBuildOnMake = buildOnMake;
    myRootElement = rootElement;
    myOutputPath = outputPath;
    myProperties = new HashMap<ArtifactPropertiesProvider, ArtifactProperties<?>>();
    resetProperties();
  }

  private void resetProperties() {
    myProperties.clear();
    for (ArtifactPropertiesProvider provider : ArtifactPropertiesProvider.getProviders()) {
      if (provider.isAvailableFor(myArtifactType)) {
        myProperties.put(provider, provider.createProperties(myArtifactType));
      }
    }
  }

  @NotNull
  public ArtifactType getArtifactType() {
    return myArtifactType;
  }

  public String getName() {
    return myName;
  }

  public boolean isBuildOnMake() {
    return myBuildOnMake;
  }

  @NotNull
  public CompositePackagingElement<?> getRootElement() {
    return myRootElement;
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public Collection<? extends ArtifactPropertiesProvider> getPropertiesProviders() {
    return Collections.unmodifiableCollection(myProperties.keySet());
  }

  public ArtifactImpl createCopy() {
    final ArtifactImpl artifact = new ArtifactImpl(myName, myArtifactType, myBuildOnMake, myRootElement, myOutputPath);
    for (Map.Entry<ArtifactPropertiesProvider, ArtifactProperties<?>> entry : myProperties.entrySet()) {
      final ArtifactProperties newProperties = artifact.myProperties.get(entry.getKey());
      //noinspection unchecked
      newProperties.loadState(entry.getValue().getState());
    }
    return artifact;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NonNls @Override
  public String toString() {
    return "artifact:" + myName;
  }

  public void setRootElement(CompositePackagingElement<?> root) {
    myRootElement = root;
  }

  public void setProperties(ArtifactPropertiesProvider provider, ArtifactProperties<?> properties) {
    if (properties != null) {
      myProperties.put(provider, properties);
    }
    else {
      myProperties.remove(provider);
    }
  }

  public void setArtifactType(@NotNull ArtifactType selected) {
    myArtifactType = selected;
    resetProperties();
  }

  public void setBuildOnMake(boolean buildOnMake) {
    myBuildOnMake = buildOnMake;
  }

  public void setOutputPath(String outputPath) {
    myOutputPath = outputPath;
  }

  public ArtifactProperties<?> getProperties(@NotNull ArtifactPropertiesProvider provider) {
    return myProperties.get(provider);
  }

  public void copyFrom(ArtifactImpl modified) {
    myName = modified.getName();
    myOutputPath = modified.getOutputPath();
    myBuildOnMake = modified.isBuildOnMake();
    myRootElement = modified.getRootElement();
    myProperties = modified.myProperties;
    myArtifactType = modified.getArtifactType();
  }
}
