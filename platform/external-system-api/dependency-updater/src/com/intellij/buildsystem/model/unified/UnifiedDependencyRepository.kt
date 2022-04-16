package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependencyRepository
import com.intellij.openapi.util.NlsSafe


data class UnifiedDependencyRepository(
  val id: String?,
  val name: String?,
  val url: String?
) : BuildDependencyRepository {

  @get:NlsSafe
  val displayName: String = buildString {
    append('[')
    if (!name.isNullOrBlank()) append("name='$name'")
    if (!id.isNullOrBlank()) {
      if (count() > 1) append(", ")
      append("id='")
      append(id)
      append("'")
    }
    if (!url.isNullOrBlank()) {
      if (count() > 1) append(", ")
      append("url='")
      append(url)
      append("'")
    }
    if (count() == 1) append("#NO_DATA#")
    append(']')
  }
}
