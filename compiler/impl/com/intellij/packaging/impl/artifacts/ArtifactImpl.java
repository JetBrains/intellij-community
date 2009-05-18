package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactImpl implements ModifiableArtifact {
  private ArtifactRootElement<?> myRootElement;
  private String myName;
  private boolean myBuildOnMake;
  private String myOutputPath;
  private ArtifactType myArtifactType;
  private Map<ArtifactPropertiesProvider, ArtifactProperties<?>> myProperties;

  public ArtifactImpl(@NotNull String name, @NotNull ArtifactType artifactType, boolean buildOnMake, @NotNull ArtifactRootElement<?> rootElement, String outputPath) {
    myName = name;
    myArtifactType = artifactType;
    myBuildOnMake = buildOnMake;
    myRootElement = rootElement;
    myOutputPath = outputPath;
    myProperties = new HashMap<ArtifactPropertiesProvider, ArtifactProperties<?>>();
    for (ArtifactPropertiesProvider provider : ArtifactPropertiesProvider.getProviders()) {
      if (provider.isAvailableFor(artifactType)) {
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
  public ArtifactRootElement<?> getRootElement() {
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

  public void setName(String name) {
    myName = name;
  }

  public void setRootElement(ArtifactRootElement<?> root) {
    myRootElement = root;
  }

  public void setProperties(ArtifactPropertiesProvider provider, ArtifactProperties<?> properties) {
    myProperties.put(provider, properties);
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
}
