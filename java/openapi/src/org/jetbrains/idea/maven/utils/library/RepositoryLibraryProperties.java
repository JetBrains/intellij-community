/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.function.Function;

/**
 * @author nik
 */
public class RepositoryLibraryProperties extends LibraryProperties<RepositoryLibraryProperties> {
  private JpsMavenRepositoryLibraryDescriptor myDescriptor;

  public RepositoryLibraryProperties() {
  }

  public RepositoryLibraryProperties(String mavenId, final boolean includeTransitiveDependencies) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(mavenId, includeTransitiveDependencies);
  }

  public RepositoryLibraryProperties(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this(groupId, artifactId, version, true);
  }

  public RepositoryLibraryProperties(@NotNull String groupId, @NotNull String artifactId, @NotNull String version, boolean includeTransitiveDependencies) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version, includeTransitiveDependencies);
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
  public void loadState(RepositoryLibraryProperties state) {
    myDescriptor = state.myDescriptor;
  }

  @Attribute("maven-id")
  public String getMavenId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getMavenId);
  }

  public void setMavenId(String mavenId) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(mavenId, isIncludeTransitiveDependencies());
  }

  @Attribute("include-transitive-deps")
  public boolean isIncludeTransitiveDependencies() {
    return myDescriptor == null || myDescriptor.isIncludeTransitiveDependencies();
  }

  public void setIncludeTransitiveDependencies(boolean value) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getMavenId(), value);
  }

  public String getGroupId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getGroupId);
  }

  public String getArtifactId() {
    return call(JpsMavenRepositoryLibraryDescriptor::getArtifactId);
  }

  public String getVersion() {
    return call(JpsMavenRepositoryLibraryDescriptor::getVersion);
  }

  public void changeVersion(String version) {
    myDescriptor = new JpsMavenRepositoryLibraryDescriptor(getGroupId(), getArtifactId(), version, myDescriptor.isIncludeTransitiveDependencies());
  }

  private String call(Function<JpsMavenRepositoryLibraryDescriptor, String> method) {
    final JpsMavenRepositoryLibraryDescriptor descriptor = myDescriptor;
    return descriptor != null ? method.apply(descriptor) : null;
  }

  @NotNull
  public JpsMavenRepositoryLibraryDescriptor getRepositoryLibraryDescriptor() {
    return myDescriptor != null ? myDescriptor : new JpsMavenRepositoryLibraryDescriptor(null, true);
  }
}
