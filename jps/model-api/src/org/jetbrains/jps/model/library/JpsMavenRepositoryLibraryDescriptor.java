// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public class JpsMavenRepositoryLibraryDescriptor {
  public static final String DEFAULT_PACKAGING = "jar";

  private final String myMavenId;
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myPackaging;
  private final boolean myIncludeTransitiveDependencies;
  private final List<String> myExcludedDependencies;

  private final String myJarRepositoryId;

  private final List<ArtifactVerification> myArtifactsVerification;

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this(groupId, artifactId, version, true, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             boolean includeTransitiveDependencies, @NotNull List<String> excludedDependencies) {
    this(groupId, artifactId, version, DEFAULT_PACKAGING, includeTransitiveDependencies, excludedDependencies);
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             final @NotNull String packaging, boolean includeTransitiveDependencies,
                                             @NotNull List<String> excludedDependencies) {
    this(groupId, artifactId, version, packaging, includeTransitiveDependencies, excludedDependencies, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             final @NotNull String packaging, boolean includeTransitiveDependencies,
                                             @NotNull List<String> excludedDependencies,
                                             @NotNull List<ArtifactVerification> metadata) {
    this(groupId, artifactId, version, packaging, includeTransitiveDependencies, excludedDependencies, metadata, null);
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             final @NotNull String packaging, boolean includeTransitiveDependencies,
                                             @NotNull List<String> excludedDependencies,
                                             @NotNull List<ArtifactVerification> metadata,
                                             @Nullable String jarRepositoryId) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myPackaging = packaging;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
    myExcludedDependencies = excludedDependencies;
    myMavenId = groupId + ":" + artifactId + ":" + version;
    myArtifactsVerification = metadata;
    myJarRepositoryId = jarRepositoryId;
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId) {
    this(mavenId, true, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             boolean includeTransitiveDependencies) {
    this(groupId, artifactId, version, includeTransitiveDependencies, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    this(mavenId, DEFAULT_PACKAGING, includeTransitiveDependencies, excludedDependencies);
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, @NotNull String packaging, boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    this(mavenId, packaging, includeTransitiveDependencies, excludedDependencies, Collections.emptyList(), null);
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, boolean includeTransitiveDependencies, List<String> excludedDependencies,
                                             @NotNull List<ArtifactVerification> artifactsVerification,
                                             @Nullable String jarRepositoryId) {
    this(mavenId, DEFAULT_PACKAGING, includeTransitiveDependencies, excludedDependencies, artifactsVerification,
         jarRepositoryId);
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, @NotNull String packaging, boolean includeTransitiveDependencies, List<String> excludedDependencies,
                                             @NotNull List<ArtifactVerification> artifactsVerification,
                                             @Nullable String jarRepositoryId) {
    myMavenId = mavenId;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
    myExcludedDependencies = excludedDependencies;
    myPackaging = packaging;
    myArtifactsVerification = artifactsVerification;
    myJarRepositoryId = jarRepositoryId;

    if (mavenId == null) {
      myGroupId = myArtifactId = myVersion = null;
    }
    else {
      String[] parts = mavenId.split(":");
      myGroupId = parts.length > 0 ? parts[0] : null;
      myArtifactId = parts.length > 1 ? parts[1] : null;
      myVersion = parts.length > 2 ? parts[2] : null;
    }
  }

  public String getMavenId() {
    return myMavenId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public boolean isIncludeTransitiveDependencies() {
    return myIncludeTransitiveDependencies;
  }

  /**
   * Returns list of excluded transitive dependencies in {@code "<groupId>:<artifactId>"} format.
   */
  public List<String> getExcludedDependencies() {
    return myExcludedDependencies;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getPackaging() {
    return myPackaging;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JpsMavenRepositoryLibraryDescriptor that = (JpsMavenRepositoryLibraryDescriptor)o;
    return myIncludeTransitiveDependencies == that.myIncludeTransitiveDependencies &&
      myMavenId.equals(that.myMavenId) &&
      myPackaging.equals(that.myPackaging) &&
      myExcludedDependencies.equals(that.myExcludedDependencies) &&
      myArtifactsVerification.equals(that.myArtifactsVerification) &&
      Objects.equals(myJarRepositoryId, that.myJarRepositoryId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenId, myPackaging, myIncludeTransitiveDependencies, myExcludedDependencies, myArtifactsVerification,
                        myJarRepositoryId);
  }

  @Override
  public String toString() {
    return myMavenId != null ? myMavenId : "null";
  }

  public boolean isVerifySha256Checksum() {
    return ContainerUtil.exists(myArtifactsVerification, it -> it.getSha256sum() != null);
  }

  public List<ArtifactVerification> getArtifactsVerification() {
    return myArtifactsVerification;
  }

  public String getJarRepositoryId() {
    return myJarRepositoryId;
  }

  public static class ArtifactVerification {
    private final String url;
    private final String sha256sum;

    public ArtifactVerification(@NotNull String url, @NotNull String sha256sum) {
      this.url = url;
      this.sha256sum = sha256sum;
    }

    public String getSha256sum() {
      return sha256sum;
    }

    public String getUrl() {
      return url;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ArtifactVerification that = (ArtifactVerification)o;
      return Objects.equals(url, that.url) && Objects.equals(sha256sum, that.sha256sum);
    }

    @Override
    public int hashCode() {
      return Objects.hash(url, sha256sum);
    }
  }
}
