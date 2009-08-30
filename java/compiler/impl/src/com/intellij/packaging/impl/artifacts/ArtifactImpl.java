package com.intellij.packaging.impl.artifacts;

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
public class ArtifactImpl implements ModifiableArtifact {
  private CompositePackagingElement<?> myRootElement;
  private String myName;
  private boolean myBuildOnMake;
  private boolean myClearOutputDirectoryOnRebuild;
  private String myOutputPath;
  private ArtifactType myArtifactType;
  private Map<ArtifactPropertiesProvider, ArtifactProperties<?>> myProperties;

  public ArtifactImpl(@NotNull String name, @NotNull ArtifactType artifactType, boolean buildOnMake, @NotNull CompositePackagingElement<?> rootElement,
                      String outputPath,
                      boolean clearOutputDirectoryOnRebuild) {
    myName = name;
    myArtifactType = artifactType;
    myBuildOnMake = buildOnMake;
    myRootElement = rootElement;
    myOutputPath = outputPath;
    myClearOutputDirectoryOnRebuild = clearOutputDirectoryOnRebuild;
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
  public CompositePackagingElement<?> getRootElement() {
    return myRootElement;
  }

  public void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild) {
    myClearOutputDirectoryOnRebuild = clearOutputDirectoryOnRebuild;
  }

  public boolean isClearOutputDirectoryOnRebuild() {
    return myClearOutputDirectoryOnRebuild;
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public Collection<? extends ArtifactPropertiesProvider> getPropertiesProviders() {
    return Collections.unmodifiableCollection(myProperties.keySet());
  }

  public ArtifactImpl createCopy() {
    final ArtifactImpl artifact = new ArtifactImpl(myName, myArtifactType, myBuildOnMake, myRootElement, myOutputPath,
                                                   myClearOutputDirectoryOnRebuild);
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
    myProperties.put(provider, properties);
  }

  public void setArtifactType(@NotNull ArtifactType selected) {
    myArtifactType = selected;
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
    myClearOutputDirectoryOnRebuild = modified.isClearOutputDirectoryOnRebuild();
    myProperties = modified.myProperties;
    myArtifactType = modified.getArtifactType();
  }
}
