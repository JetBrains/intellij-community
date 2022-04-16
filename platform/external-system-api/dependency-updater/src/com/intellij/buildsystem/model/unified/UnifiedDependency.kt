package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependency
import com.intellij.openapi.util.NlsSafe

data class UnifiedDependency(
  val coordinates: UnifiedCoordinates,
  val scope: String?
) : BuildDependency {

  constructor(groupId: String?,
              artifactId: String?,
              version: String?,
              configuration: String? = null) :
    this(UnifiedCoordinates(groupId, artifactId, version), configuration)

  @get:NlsSafe
  override val displayName by lazy {
    buildString {
      append(coordinates.displayName)
      if (scope != null) {
        append(" [scope=")
        append(scope)
        append("]")
      }
    }
  }
}
