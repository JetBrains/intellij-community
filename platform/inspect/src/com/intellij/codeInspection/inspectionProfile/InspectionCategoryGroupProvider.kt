// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionToolWrapper

private const val CATEGORY_PROVIDER_PREFIX = "category:"

internal class InspectionCategoryGroupProvider : InspectionGroupProvider {

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    if (!groupId.startsWith(CATEGORY_PROVIDER_PREFIX)) return null
    val category = groupId.substring(CATEGORY_PROVIDER_PREFIX.length)
    if (category.isBlank()) return null
    return object : YamlInspectionGroup {

      override val groupId: String = groupId

      override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
        return try {
          category.isAncestor(tool.groupPath.makeCategoryId())
        }
        catch (e: AssertionError) {
          false
        }
      }
    }
  }
}

private fun String.isAncestor(categoryId: String): Boolean {
  if (this.isEmpty() || categoryId.isEmpty()) return false
  val normalizedAncestor = this.removeSuffix("/") + "/"
  val normalizedCategoryId = categoryId.removeSuffix("/") + "/"
  return normalizedCategoryId.startsWith(normalizedAncestor)
}

fun Array<String>.makeCategoryId(): String {
  return joinToString("/") { escapeToolGroupPathElement(it) }
}

fun escapeToolGroupPathElement(path: String): String {
  return path
    .replace("/", "_")
}