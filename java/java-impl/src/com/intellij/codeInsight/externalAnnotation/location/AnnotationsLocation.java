// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation.location;

import com.intellij.openapi.util.text.StringUtil;
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

  private final @NotNull String myGroupId;
  private final @NotNull String myArtifactId;
  private final @NotNull String myVersion;

  private final @NotNull List<String> myRepositoryUrls;


  public AnnotationsLocation(@NotNull String groupId,
                             @NotNull String artifactId,
                             @NotNull String version,
                             String... repositoryUrls) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myRepositoryUrls = new SmartList<>(repositoryUrls);
  }


  public @NotNull String getGroupId() {
    return myGroupId;
  }

  public @NotNull String getArtifactId() {
    return myArtifactId;
  }

  public @NotNull String getVersion() {
    return myVersion;
  }

  public @NotNull Collection<String> getRepositoryUrls() {
    return Collections.unmodifiableList(myRepositoryUrls);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnnotationsLocation location)) return false;

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

  @Override
  public String toString() {
    return "[AnnotationLocation '" + myGroupId + ":" + myArtifactId + ":" + myVersion + "'@'" + StringUtil.join(myRepositoryUrls, "; ") + "']";
  }
}
