// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Library maven coordinates bean
 */
public class ExternalLibraryDescriptor {
  private static final Logger LOG = Logger.getInstance(ExternalLibraryDescriptor.class);
  @NotNull private final String myLibraryGroupId;
  @NotNull private final String myLibraryArtifactId;
  private final String myMinVersion;
  private final String myMaxVersion;
  private final String myPreferredVersion;

  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId) {
    this(libraryGroupId, libraryArtifactId, null, null, null);
  }

  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId, @Nullable String minVersion, @Nullable String maxVersion) {
    this(libraryGroupId, libraryArtifactId, minVersion, maxVersion, null);
  }

  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId,
                                   @Nullable String minVersion, @Nullable String maxVersion, @Nullable String preferredVersion) {
    myLibraryGroupId = libraryGroupId;
    myLibraryArtifactId = libraryArtifactId;
    myMinVersion = minVersion;
    myMaxVersion = maxVersion;
    myPreferredVersion = preferredVersion;
    if (preferredVersion != null && maxVersion != null) {
      LOG.assertTrue(VersionComparatorUtil.compare(preferredVersion, maxVersion) <= 0,
                     "Preferred version (" + preferredVersion + ") must not be newer than max version (" + maxVersion + ")");
    }
    if (preferredVersion != null && minVersion != null) {
      LOG.assertTrue(VersionComparatorUtil.compare(minVersion, preferredVersion) <= 0,
                     "Preferred version (" + preferredVersion + ") must not be older than min version (" + minVersion + ")");
    }
    if (minVersion != null && maxVersion != null) {
      LOG.assertTrue(VersionComparatorUtil.compare(minVersion, maxVersion) <= 0,
                     "Max version (" + maxVersion + ") must not be older than min version (" + minVersion + ")");
    }
  }

  @NotNull
  public String getLibraryGroupId() {
    return myLibraryGroupId;
  }

  @NotNull
  public String getLibraryArtifactId() {
    return myLibraryArtifactId;
  }

  @Nullable
  public String getMinVersion() {
    return myMinVersion;
  }

  @Nullable
  public String getMaxVersion() {
    return myMaxVersion;
  }

  @Nullable
  public String getPreferredVersion() {
    return myPreferredVersion;
  }

  @NotNull
  public String getPresentableName() {
    return myLibraryArtifactId;
  }

  @NotNull
  public List<String> getLibraryClassesRoots() {
    return Collections.emptyList();
  }
}
