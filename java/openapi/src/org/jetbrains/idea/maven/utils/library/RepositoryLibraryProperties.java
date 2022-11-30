// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XCollection.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class RepositoryLibraryProperties extends LibraryProperties<RepositoryLibraryProperties> {
  private JpsMavenRepositoryLibraryDescriptor myDescriptor;

  public RepositoryLibraryProperties() {
  }

  public RepositoryLibraryProperties(JpsMavenRepositoryLibraryDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  public RepositoryLibraryProperties(String mavenId, final boolean includeTransitiveDependencies) {
    this(new JpsMavenRepositoryLibraryDescriptor(mavenId, includeTransitiveDependencies, Collections.emptyList()));
  }

  public RepositoryLibraryProperties(String mavenId, String packaging, final boolean includeTransitiveDependencies) {
    this(new JpsMavenRepositoryLibraryDescriptor(mavenId, packaging, includeTransitiveDependencies, Collections.emptyList()));
  }

  public RepositoryLibraryProperties(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this(groupId, artifactId, version, true, ContainerUtil.emptyList());
  }

  public RepositoryLibraryProperties(@NotNull String groupId,
                                     @NotNull String artifactId,
                                     @NotNull String version,
                                     boolean includeTransitiveDependencies, @NotNull List<String> excludedDependencies) {
    this(new JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version, includeTransitiveDependencies,
                                                           excludedDependencies));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof RepositoryLibraryProperties && Comparing.equal(myDescriptor, ((RepositoryLibraryProperties)obj).myDescriptor);
  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(getMavenId());
  }

  @Override
  public RepositoryLibraryProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull RepositoryLibraryProperties state) {
    myDescriptor = state.myDescriptor;
  }

  @Attribute("maven-id")
  public @NlsSafe String getMavenId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getMavenId, null);
  }

  @Attribute("packaging")
  public String getPackaging() {
    return call(JpsMavenRepositoryLibraryDescriptor::getPackaging, JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING);
  }

  public void setMavenId(String mavenId) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(mavenId, getPackaging(), isIncludeTransitiveDependencies(),
                                                           getExcludedDependencies(), isEnableSha256Checksum(), getArtifactsVerification(),
                                                           getJarRepositoryId());
  }

  public void setPackaging(String packaging) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), packaging, isIncludeTransitiveDependencies(),
                                                           getExcludedDependencies(), isEnableSha256Checksum(), getArtifactsVerification(),
                                                           getJarRepositoryId());
  }


  @Attribute("include-transitive-deps")
  public boolean isIncludeTransitiveDependencies() {
    return call(JpsMavenRepositoryLibraryDescriptor::isIncludeTransitiveDependencies, Boolean.TRUE);
  }

  public void setIncludeTransitiveDependencies(boolean value) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), value,
                                                           getExcludedDependencies(), isEnableSha256Checksum(), getArtifactsVerification(),
                                                           getJarRepositoryId());
  }


  @Attribute("jar-repository-id")
  public String getJarRepositoryId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getJarRepositoryId, JpsMavenRepositoryLibraryDescriptor.JAR_REPOSITORY_ID_NOT_SET);
  }

  public void setJarRepositoryId(String jarRepositoryId) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), isIncludeTransitiveDependencies(),
                                                           getExcludedDependencies(), isEnableSha256Checksum(),
                                                           getArtifactsVerification(), jarRepositoryId);
  }

  @Attribute("verify-sha256-checksum")
  public boolean isEnableSha256Checksum() {
    return call(JpsMavenRepositoryLibraryDescriptor::isVerifySha256Checksum, JpsMavenRepositoryLibraryDescriptor.VERIFY_SHA256_CHECKSUM_DEFAULT);
  }

  @Attribute("verify-sha256-checksum")
  public void setEnableSha256Checksum(boolean value) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), isIncludeTransitiveDependencies(),
                                                           getExcludedDependencies(), value, getArtifactsVerification(),
                                                           getJarRepositoryId());
  }

  public String getGroupId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getGroupId, null);
  }

  public String getArtifactId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getArtifactId, null);
  }

  public String getVersion() {
    return call(JpsMavenRepositoryLibraryDescriptor::getVersion, null);
  }

  public void changeVersion(String version) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getGroupId(), getArtifactId(), version, getPackaging(),
                                                           isIncludeTransitiveDependencies(), getExcludedDependencies(),
                                                           isEnableSha256Checksum(), getArtifactsVerification(),
                                                           getJarRepositoryId());
  }

  private <T> T call(Function<? super JpsMavenRepositoryLibraryDescriptor, ? extends T> method, final T defaultValue) {
    final JpsMavenRepositoryLibraryDescriptor descriptor = myDescriptor;
    return descriptor != null ? method.apply(descriptor) : defaultValue;
  }

  /**
   * Returns list of excluded transitive dependencies in {@code "<groupId>:<artifactId>"} format.
   */
  @Transient
  public List<String> getExcludedDependencies() {
    return call(JpsMavenRepositoryLibraryDescriptor::getExcludedDependencies, Collections.emptyList());
  }

  public void setExcludedDependencies(List<String> dependencyMavenIds) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), isIncludeTransitiveDependencies(),
                                                           dependencyMavenIds, isEnableSha256Checksum(), getArtifactsVerification(),
                                                           getJarRepositoryId());
  }


  @Transient
  public List<ArtifactVerification> getArtifactsVerification() {
    return call(JpsMavenRepositoryLibraryDescriptor::getArtifactsVerification, Collections.emptyList());
  }

  public void setArtifactsVerification(@Nullable List<ArtifactVerification> artifactsVerification) {
    List<ArtifactVerification> effectiveValue = artifactsVerification == null ? Collections.emptyList() : artifactsVerification;
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), isIncludeTransitiveDependencies(),
                                                           getExcludedDependencies(), isEnableSha256Checksum(), effectiveValue,
                                                           getJarRepositoryId());
  }

  @SuppressWarnings("unused") //we need to have a separate method here because XmlSerializer fails if the returned list is unmodifiable
  @XCollection(propertyElementName = "exclude", elementName = "dependency", valueAttributeName = "maven-id")
  public List<String> getExcludedDependenciesBean() {
    return myDescriptor != null ? new ArrayList<>(myDescriptor.getExcludedDependencies()) : new ArrayList<>();
  }

  @SuppressWarnings("unused") //used by XmlSerializer
  public void setExcludedDependenciesBean(List<String> dependencyMavenIds) {
    setExcludedDependencies(dependencyMavenIds);
  }

  @NotNull
  public JpsMavenRepositoryLibraryDescriptor getRepositoryLibraryDescriptor() {
    return myDescriptor != null ? myDescriptor : new JpsMavenRepositoryLibraryDescriptor(null, true, Collections.emptyList());
  }

  @SuppressWarnings("unused") //used by XmlSerializer
  @XCollection(propertyElementName = "verification", style = Style.v2)
  public List<ArtifactVerificationProperties> getArtifactsVerificationBean() {
    List<ArtifactVerification> artifactsVerification = getArtifactsVerification();
    return artifactsVerification == null ? null : ContainerUtil.map(artifactsVerification, ArtifactVerificationProperties::new);
  }

  @SuppressWarnings("unused") //used by XmlSerializer
  public void setArtifactsVerificationBean(@Nullable List<ArtifactVerificationProperties> properties) {
    setArtifactsVerification(properties == null ? null : ContainerUtil.map(properties, ArtifactVerificationProperties::getDescriptor));
  }

  public RepositoryLibraryProperties cloneAndChange(Consumer<RepositoryLibraryProperties> transform) {
    RepositoryLibraryProperties newProperties = new RepositoryLibraryProperties(myDescriptor);
    transform.accept(newProperties);
    return newProperties;
  }

  public void disableVerification() {
    setEnableSha256Checksum(false);
    setArtifactsVerification(Collections.emptyList());
  }

  public void unbindRemoteRepository() {
    setJarRepositoryId(JpsMavenRepositoryLibraryDescriptor.JAR_REPOSITORY_ID_NOT_SET);
  }

  @Tag("artifact")
  public static class ArtifactVerificationProperties {
    @NotNull
    private ArtifactVerification myDescriptor;

    @SuppressWarnings("unused") //used by XmlSerializer
    private ArtifactVerificationProperties() {
      this(new ArtifactVerification("", null));
    }

    public ArtifactVerificationProperties(@NotNull ArtifactVerification descriptor) {
        myDescriptor = descriptor;
    }

    @Attribute("url")
    public String getUrl() {
      return myDescriptor.getUrl();
    }

    public void setUrl(String url) {
      myDescriptor = new ArtifactVerification(url, getSha256sum());
    }

    @Tag("sha256sum")
    public String getSha256sum() {
      return myDescriptor.getSha256sum();
    }

    @SuppressWarnings("unused") //used by XmlSerializer
    public void setSha256sum(String sha256sum) {
      myDescriptor = new ArtifactVerification(getUrl(), sha256sum);
    }

    @NotNull
    private ArtifactVerification getDescriptor() {
      assert !myDescriptor.getUrl().isEmpty(); // Ensure we have read url value
      return myDescriptor;
    }
  }
}
