// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(mavenId, getPackaging(), isIncludeTransitiveDependencies(), getExcludedDependencies());
  }

  public void setPackaging(String packaging) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), packaging,  isIncludeTransitiveDependencies(), getExcludedDependencies());
  }


  @Attribute("include-transitive-deps")
  public boolean isIncludeTransitiveDependencies() {
    return call(JpsMavenRepositoryLibraryDescriptor::isIncludeTransitiveDependencies, Boolean.TRUE);
  }

  public void setIncludeTransitiveDependencies(boolean value) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), value, getExcludedDependencies());
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
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getGroupId(), getArtifactId(), version, getPackaging(), isIncludeTransitiveDependencies(), getExcludedDependencies());
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
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), getPackaging(), isIncludeTransitiveDependencies(), dependencyMavenIds);
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
}
