// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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

  @NotNull private final Collection<String> myRepositoryUrls = new SmartList<>();


  public AnnotationsLocation(@NotNull String groupId,
                             @NotNull String artifactId,
                             @NotNull String version) {
    this.myGroupId = groupId;
    this.myArtifactId = artifactId;
    this.myVersion = version;
  }

  public AnnotationsLocation inRepository(@NotNull String url) {
    myRepositoryUrls.add(url);
    return this;
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
    return myRepositoryUrls;
  }
}
