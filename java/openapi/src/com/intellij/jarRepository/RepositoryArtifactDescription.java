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
package com.intellij.jarRepository;

import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

/**
 * @author Eugene Zhuravlev
 */
public class RepositoryArtifactDescription {
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myPackaging;
  private final String myClassifier;
  private final String myClassNames;
  private final String myRepositoryId;

  public RepositoryArtifactDescription(RepositoryLibraryProperties libProperties, String packaging, String classifier) {
    this(libProperties.getGroupId(), libProperties.getArtifactId(), libProperties.getVersion(), packaging, classifier);
  }

  public RepositoryArtifactDescription(String groupId,
                                       String artifactId,
                                       String version,
                                       String packaging,
                                       String classifier) {
    this(groupId, artifactId, version, packaging, classifier, null, null);
  }

  public RepositoryArtifactDescription(String groupId,
                                       String artifactId,
                                       String version,
                                       String packaging,
                                       String classifier,
                                       String classNames,
                                       String repositoryId) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myPackaging = packaging;
    myClassifier = classifier;
    myClassNames = classNames;
    myRepositoryId = repositoryId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getPackaging() {
    return myPackaging;
  }

  public String getClassifier() {
    return myClassifier;
  }

  public String getClassNames() {
    return myClassNames;
  }

  public String getRepositoryId() {
    return myRepositoryId;
  }

  @Override
  public String toString() {
    return "RepositoryArtifactDescription{" +
           "myGroupId='" + myGroupId + '\'' +
           ", myArtifactId='" + myArtifactId + '\'' +
           ", myVersion='" + myVersion + '\'' +
           ", myPackaging='" + myPackaging + '\'' +
           ", myClassifier='" + myClassifier + '\'' +
           ", myClassNames='" + myClassNames + '\'' +
           ", myRepositoryId='" + myRepositoryId + '\'' +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RepositoryArtifactDescription that = (RepositoryArtifactDescription)o;

    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    if (myPackaging != null ? !myPackaging.equals(that.myPackaging) : that.myPackaging != null) return false;
    if (myClassifier != null ? !myClassifier.equals(that.myClassifier) : that.myClassifier != null) return false;
    if (myClassNames != null ? !myClassNames.equals(that.myClassNames) : that.myClassNames != null) return false;
    if (myRepositoryId != null ? !myRepositoryId.equals(that.myRepositoryId) : that.myRepositoryId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId != null ? myGroupId.hashCode() : 0;
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myPackaging != null ? myPackaging.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    result = 31 * result + (myClassNames != null ? myClassNames.hashCode() : 0);
    result = 31 * result + (myRepositoryId != null ? myRepositoryId.hashCode() : 0);
    return result;
  }
}
