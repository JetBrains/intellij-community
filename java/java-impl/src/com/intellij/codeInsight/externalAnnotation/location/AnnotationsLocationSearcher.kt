// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation.location

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library

/**
 * Component that looks for an external annotations locations using plugin providers.
 */
public class AnnotationsLocationSearcher {
  public companion object {
    public fun findAnnotationsLocation(project: Project,
                                       library: Library,
                                       artifactId: String?,
                                       groupId: String?,
                                       version: String?): Collection<AnnotationsLocation> {
      return AnnotationsLocationProvider.EP_NAME.extensionList.flatMap { it.getLocations(project, library, artifactId, groupId, version) }
    }
  }
}