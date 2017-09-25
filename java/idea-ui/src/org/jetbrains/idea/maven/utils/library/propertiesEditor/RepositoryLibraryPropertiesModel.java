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

public class RepositoryLibraryPropertiesModel {
  private String version;
  private boolean downloadSources;
  private boolean downloadJavaDocs;
  private boolean includeTransitiveDependencies;

  public RepositoryLibraryPropertiesModel(String version, boolean downloadSources, boolean downloadJavaDocs) {
    this(version, downloadSources, downloadJavaDocs, true);
  }

  public RepositoryLibraryPropertiesModel(String version, boolean downloadSources, boolean downloadJavaDocs,
                                          boolean includeTransitiveDependencies) {
    this.version = version;
    this.downloadSources = downloadSources;
    this.downloadJavaDocs = downloadJavaDocs;
    this.includeTransitiveDependencies = includeTransitiveDependencies;
  }

  public RepositoryLibraryPropertiesModel clone() {
    return new RepositoryLibraryPropertiesModel(version, downloadSources, downloadJavaDocs, includeTransitiveDependencies);
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

  public boolean isDownloadSources() {
    return downloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    this.downloadSources = downloadSources;
  }

  public boolean isDownloadJavaDocs() {
    return downloadJavaDocs;
  }

  public void setDownloadJavaDocs(boolean downloadJavaDocs) {
    this.downloadJavaDocs = downloadJavaDocs;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RepositoryLibraryPropertiesModel model = (RepositoryLibraryPropertiesModel)o;

    if (downloadSources != model.downloadSources) return false;
    if (downloadJavaDocs != model.downloadJavaDocs) return false;
    if (includeTransitiveDependencies != model.includeTransitiveDependencies) return false;
    if (version != null ? !version.equals(model.version) : model.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (downloadSources ? 1 : 0);
    result = 31 * result + (downloadJavaDocs ? 1 : 0);
    result = 31 * result + (includeTransitiveDependencies ? 1 : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }
}
