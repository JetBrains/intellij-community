// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface AnnotationsLocationProvider {

  ExtensionPointName<AnnotationsLocationProvider> EP_NAME = ExtensionPointName.create("com.intellij.java.externalAnnotation.locationProvider");

  /**
   * Get annotations locations for a library.
   * <p/>
   * Returned result will be used to retrieve annotations and attach them to library
   * @param library idea library.
   * @param artifactId - Maven artifact id of a library, if known
   * @param groupId - Maven group id of a library, if known
   * @param version - Maven version of a library, if known
   * @return information about annotations location, empty collection if this library is not known to provider
   */
  @NotNull
  Collection<AnnotationsLocation> getLocations(@NotNull Library library,
                                               @Nullable String artifactId,
                                               @Nullable String groupId,
                                               @Nullable String version);
}
