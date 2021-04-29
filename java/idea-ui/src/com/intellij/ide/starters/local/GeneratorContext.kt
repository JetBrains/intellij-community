package com.intellij.ide.starters.local

import com.intellij.openapi.vfs.VirtualFile

internal class GeneratorContext(
  val starterId: String,
  val moduleName: String,
  val group: String,
  val artifact: String,
  val version: String,
  val testRunnerId: String?,
  private val languageId: String,
  private val libraryIds: Set<String>,
  private val dependencyConfig: DependencyConfig,
  private val properties: Map<String, String>,
  val assets: List<GeneratorAsset>,
  val outputDirectory: VirtualFile
) {
  fun hasLanguage(languageId: String): Boolean {
    return this.languageId == languageId
  }

  fun hasLibrary(libraryId: String): Boolean {
    return libraryIds.contains(libraryId)
  }

  fun hasAnyLibrary(vararg ids: String): Boolean {
    return ids.any { libraryIds.contains(it) }
  }

  fun hasAllLibraries(vararg ids: String): Boolean {
    return ids.all { libraryIds.contains(it) }
  }

  fun getVersion(group: String, artifact: String): String? {
    return dependencyConfig.dependencies.find { it.group == group && it.artifact == artifact }?.version
  }

  fun getBomProperty(propertyId: String): String? {
    return dependencyConfig.properties[propertyId]
  }

  fun getProperty(propertyId: String): String? {
    return properties[propertyId]
  }

  /**
   * Renders propertyId as ${propertyId}.
   */
  fun asPlaceholder(propertyId: String) : String {
    return "\${$propertyId}"
  }
}