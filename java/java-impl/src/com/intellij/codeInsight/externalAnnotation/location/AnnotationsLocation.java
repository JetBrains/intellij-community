// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Location of external annotations artifact.
 * Includes group, artifact and version. Will be used with <code>annotations</code> classifier
 * <p/>
 * Repositories urls are optional.
 */
public class AnnotationsLocation {

  @NotNull private final String myGroupId;
  @NotNull private final String myArtifactId;
  @NotNull private final String myVersion;

  @NotNull private final List<String> myRepositoryUrls;


  public AnnotationsLocation(@NotNull String groupId,
                             @NotNull String artifactId,
                             @NotNull String version,
                             String... repositoryUrls) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myRepositoryUrls = new SmartList<>(repositoryUrls);
  }


  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
  public String getArtifactId() {
    return myArtifactId;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  @NotNull
  public Collection<String> getRepositoryUrls() {
    return Collections.unmodifiableList(myRepositoryUrls);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnnotationsLocation)) return false;

    AnnotationsLocation location = (AnnotationsLocation)o;

    if (!myGroupId.equals(location.myGroupId)) return false;
    if (!myArtifactId.equals(location.myArtifactId)) return false;
    if (!myVersion.equals(location.myVersion)) return false;
    if (!myRepositoryUrls.equals(location.myRepositoryUrls)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId.hashCode();
    result = 31 * result + myArtifactId.hashCode();
    result = 31 * result + myVersion.hashCode();
    result = 31 * result + myRepositoryUrls.hashCode();
    return result;
  }
}
