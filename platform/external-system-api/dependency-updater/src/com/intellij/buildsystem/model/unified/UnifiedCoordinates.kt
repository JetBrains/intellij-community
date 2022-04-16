package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependency
import com.intellij.openapi.util.NlsSafe

data class UnifiedCoordinates(
  val groupId: String?,
  val artifactId: String?,
  val version: String?
) : BuildDependency.Coordinates {

  @get:NlsSafe
  override val displayName: String = buildString {
    if (groupId != null) {
      append("$groupId")
    }
    if (artifactId != null) {
      append(":$artifactId")
    }
    if (version != null) {
      append(":$version")
    }
  }
}
