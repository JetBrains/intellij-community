// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library

/**
 * Component that looks for an external annotations locations using plugin providers.
 */
class AnnotationsLocationSearcher {
  fun findAnnotationsLocation(library: Library,
                              artifactId: String?,
                              groupId: String?,
                              version: String?): Collection<AnnotationsLocation> {
    return AnnotationsLocationProvider.EP_NAME.extensions.flatMap { it.getLocations(library, artifactId, groupId, version) }
  }

  companion object {
    fun getInstance(project: Project): AnnotationsLocationSearcher = project.getComponent(AnnotationsLocationSearcher::class.java)
  }
}