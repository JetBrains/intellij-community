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
  private final DependencyScope myPreferredScope;

  /**
   * Constructor for ExternalLibraryDescriptor. Will auto-select the newest version of the library.
   * @param libraryGroupId maven groupId of the library
   * @param libraryArtifactId maven artifactId of the library
   */
  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId) {
    this(libraryGroupId, libraryArtifactId, null, null, null);
  }

  /**
   * Constructor for ExternalLibraryDescriptor, with version constraints for library to select version from.
   * @param libraryGroupId maven groupId of the library
   * @param libraryArtifactId maven artifactId of the library
   * @param minVersion minVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   * @param maxVersion maxVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   */
  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId, @Nullable String minVersion, @Nullable String maxVersion) {
    this(libraryGroupId, libraryArtifactId, minVersion, maxVersion, null);
  }

  /**
   * Constructor for ExternalLibraryDescriptor with given version constraints and default scope
   * @param libraryGroupId maven groupId of the library
   * @param libraryArtifactId maven artifactId of the library
   * @param minVersion minVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   * @param maxVersion maxVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   * @param preferredVersion prefered version of artifact to use, or <code>null</code> for auto selection of newest version
   */
  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId,
                                   @Nullable String minVersion, @Nullable String maxVersion, @Nullable String preferredVersion) {
    this(libraryGroupId, libraryArtifactId, minVersion, maxVersion, preferredVersion, null);
  }

  /**
   * Constructor for ExternalLibraryDescriptor with given constraints for version and scope
   * @param libraryGroupId maven groupId of the library
   * @param libraryArtifactId maven artifactId of the library
   * @param minVersion minVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   * @param maxVersion maxVersion of artifact to use for auto selection of newest version, may be <code>null</code>
   * @param preferredVersion prefered version of artifact to use, or <code>null</code> for auto selection of newest version
   * @param preferredScope prefered scope of dependency to use or <code>null</code> to use default scope
   */
  public ExternalLibraryDescriptor(@NotNull String libraryGroupId, @NotNull String libraryArtifactId,
                                   @Nullable String minVersion, @Nullable String maxVersion, @Nullable String preferredVersion,
                                   @Nullable DependencyScope preferredScope) {
    myLibraryGroupId = libraryGroupId;
    myLibraryArtifactId = libraryArtifactId;
    myMinVersion = minVersion;
    myMaxVersion = maxVersion;
    myPreferredVersion = preferredVersion;
    myPreferredScope = preferredScope;
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

  @Nullable
  public DependencyScope getPreferredScope() {
    return myPreferredScope;
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
