package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependency
import com.intellij.openapi.util.NlsSafe

data class UnifiedCoordinates(
  val groupId: @NlsSafe String?,
  val artifactId: @NlsSafe String?,
  val version: @NlsSafe String?
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
