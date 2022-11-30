/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library.propertiesEditor;

import com.google.common.base.Strings;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.aether.ArtifactKind;

import javax.swing.*;
import java.util.*;

import static org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.JAR_REPOSITORY_ID_NOT_SET;
import static org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.VERIFY_SHA256_CHECKSUM_DEFAULT;

public class RepositoryLibraryPropertiesModel {
  private String version;
  private final EnumSet<ArtifactKind> myArtifactKinds = EnumSet.noneOf(ArtifactKind.class);
  private boolean includeTransitiveDependencies;
  private boolean mySha256ChecksumEnabled;
  private List<String> myExcludedDependencies;

  private final List<RemoteRepositoryDescription> myAvailableRemoteRepositories;

  private final CollectionComboBoxModel<RemoteRepositoryDescription> myRemoteRepositoryModel;


  public RepositoryLibraryPropertiesModel(String version, boolean downloadSources, boolean downloadJavaDocs) {
    this(version, downloadSources, downloadJavaDocs, true, ContainerUtil.emptyList());
  }

  public RepositoryLibraryPropertiesModel(String version, boolean downloadSources, boolean downloadJavaDocs,
                                          boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    this(version, ArtifactKind.kindsOf(downloadSources, downloadJavaDocs), includeTransitiveDependencies, excludedDependencies);
  }

  public RepositoryLibraryPropertiesModel(String version, EnumSet<ArtifactKind> artifactKinds,
                                          boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    this(version, artifactKinds, includeTransitiveDependencies, excludedDependencies, VERIFY_SHA256_CHECKSUM_DEFAULT,
         Collections.emptyList(), JAR_REPOSITORY_ID_NOT_SET);
  }

  public RepositoryLibraryPropertiesModel(String version, EnumSet<ArtifactKind> artifactKinds,
                                          boolean includeTransitiveDependencies, List<String> excludedDependencies,
                                          boolean mySha256ChecksumEnabled,
                                          List<RemoteRepositoryDescription> availableRemoteRepositories, String remoteRepositoryId) {
    this.version = version;
    this.myArtifactKinds.addAll(artifactKinds);
    this.includeTransitiveDependencies = includeTransitiveDependencies;
    this.mySha256ChecksumEnabled = mySha256ChecksumEnabled;
    myExcludedDependencies = new ArrayList<>(excludedDependencies);
    myAvailableRemoteRepositories = availableRemoteRepositories;

    List<RemoteRepositoryDescription> displayedRepositories = new ArrayList<>();
    displayedRepositories.add(null);
    displayedRepositories.addAll(availableRemoteRepositories);

    RemoteRepositoryDescription selectedRepository =
      remoteRepositoryId == null ? null : ContainerUtil.find(availableRemoteRepositories,
                                                             it -> Objects.equals(it.getId(), remoteRepositoryId));
    myRemoteRepositoryModel = new CollectionComboBoxModel<>(displayedRepositories, selectedRepository);
  }

  @Override
  public RepositoryLibraryPropertiesModel clone() {
    return new RepositoryLibraryPropertiesModel(version, myArtifactKinds, includeTransitiveDependencies,
                                                new ArrayList<>(myExcludedDependencies), mySha256ChecksumEnabled,
                                                myAvailableRemoteRepositories, getRemoteRepositoryId());
  }

  public boolean isValid() {
    return !Strings.isNullOrEmpty(version);
  }

  public boolean isIncludeTransitiveDependencies() {
    return includeTransitiveDependencies;
  }

  public void setIncludeTransitiveDependencies(boolean includeTransitiveDependencies) {
    this.includeTransitiveDependencies = includeTransitiveDependencies;
  }

  public List<String> getExcludedDependencies() {
    return myExcludedDependencies;
  }

  public void setExcludedDependencies(Collection<String> excludedDependencies) {
    myExcludedDependencies = new ArrayList<>(excludedDependencies);
  }

  public boolean isDownloadSources() {
    return myArtifactKinds.contains(ArtifactKind.SOURCES);
  }

  public void setDownloadSources(boolean downloadSources) {
    if (downloadSources) {
      myArtifactKinds.add(ArtifactKind.SOURCES);
    } else {
      myArtifactKinds.remove(ArtifactKind.SOURCES);
    }
  }

  public boolean isDownloadJavaDocs() {
    return myArtifactKinds.contains(ArtifactKind.JAVADOC);
  }

  public void setDownloadJavaDocs(boolean downloadJavaDocs) {
    if (downloadJavaDocs) {
      myArtifactKinds.add(ArtifactKind.JAVADOC);
    } else {
      myArtifactKinds.remove(ArtifactKind.JAVADOC);
    }
  }

  public boolean isDownloadAnnotations() {
    return myArtifactKinds.contains(ArtifactKind.ANNOTATIONS);
  }

  public void setDownloadAnnotations(boolean downloadAnnotations) {
    if (downloadAnnotations) {
      myArtifactKinds.add(ArtifactKind.ANNOTATIONS);
    } else {
      myArtifactKinds.remove(ArtifactKind.ANNOTATIONS);
    }
  }

  public boolean isSha256ChecksumEnabled() {
    return mySha256ChecksumEnabled;
  }

  public void setVerificationSha256Checksum(boolean value) {
    this.mySha256ChecksumEnabled = value;
  }

  public EnumSet<ArtifactKind> getArtifactKinds() {
    return EnumSet.copyOf(myArtifactKinds);
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public RemoteRepositoryDescription getRemoteRepository() {
    return myRemoteRepositoryModel.getSelected();
  }

  public String getRemoteRepositoryId() {
    RemoteRepositoryDescription repo = myRemoteRepositoryModel.getSelected();
    return repo == null ? null : repo.getId();
  }

  public ComboBoxModel<RemoteRepositoryDescription> getRemoteRepositoryModel() {
    return myRemoteRepositoryModel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RepositoryLibraryPropertiesModel model = (RepositoryLibraryPropertiesModel)o;

    if (!myArtifactKinds.equals(model.myArtifactKinds)) return false;
    if (includeTransitiveDependencies != model.includeTransitiveDependencies) return false;
    if (version != null ? !version.equals(model.version) : model.version != null) return false;
    if (!myExcludedDependencies.equals(model.myExcludedDependencies)) return false;
    if (mySha256ChecksumEnabled != model.mySha256ChecksumEnabled) return false;
    if (!Objects.equals(getRemoteRepositoryId(), model.getRemoteRepositoryId())) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myArtifactKinds.hashCode();
    result = 31 * result + (includeTransitiveDependencies ? 1 : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + myExcludedDependencies.hashCode();
    result = 31 * result + (mySha256ChecksumEnabled ? 1 : 0);
    result = 31 * result + (getRemoteRepositoryId() != null ? getRemoteRepositoryId().hashCode() : 0);
    return result;
  }
}
