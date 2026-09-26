// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("StarterUtils")

package com.intellij.ide.starters.local

import com.intellij.openapi.util.Version
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@ApiStatus.Internal
object StarterUtils {
  private val PLACEHOLDER_VERSION_PATTERN: Regex = Regex("\\$\\{(.+)}")

  private class IncorrectBomFileException(message: String) : IOException(message)

  fun parseDependencyConfig(projectTag: Element, resourcePath: String, interpolateProperties: Boolean = true): DependencyConfig {
    val properties: MutableMap<String, String> = mutableMapOf()
    val dependencies: MutableList<Dependency> = mutableListOf()

    if (projectTag.name != "project") throw IncorrectBomFileException("Incorrect root tag name ${projectTag.name}")

    val bomVersionText = projectTag.getChild("version")?.text
    if (bomVersionText.isNullOrEmpty()) throw IncorrectBomFileException("Empty BOM version for ${resourcePath}")

    val propertiesTag = projectTag.getChild("properties")
    if (propertiesTag != null) {
      for (propertyTag in propertiesTag.children) {
        val propertyName = propertyTag.name
        val propertyValue = propertyTag.text

        if (propertyName == null || propertyValue.isNullOrBlank()) {
          throw IncorrectBomFileException("Incorrect property '${propertyName}'")
        }

        properties[propertyName] = propertyValue
      }
    }

    val dependenciesTag = projectTag.getChild("dependencyManagement")?.getChild("dependencies")
    if (dependenciesTag != null) {
      for (dependencyTag in dependenciesTag.getChildren("dependency")) {
        val groupId = dependencyTag.getChild("groupId")?.text
        val artifactId = dependencyTag.getChild("artifactId")?.text
        var version = dependencyTag.getChild("version")?.text

        if (groupId.isNullOrEmpty() || artifactId.isNullOrEmpty() || version.isNullOrEmpty()) {
          throw IncorrectBomFileException("Incorrect dependency '${groupId}:${artifactId}'")
        }

        version = interpolateDependencyVersion(groupId, artifactId, version, properties, interpolateProperties)

        dependencies.add(Dependency(groupId, artifactId, version))
      }
    }

    return DependencyConfig(bomVersionText, properties, dependencies)
  }

  internal fun parseDependencyConfigVersion(projectTag: Element, resourcePath: String): Version {
    if (projectTag.name != "project") throw IncorrectBomFileException("Incorrect root tag name ${projectTag.name}")

    val bomVersionText = projectTag.getChild("version")?.text
    if (bomVersionText.isNullOrEmpty()) throw IncorrectBomFileException("Empty BOM version for ${resourcePath}")

    return Version.parseVersion(bomVersionText) ?: error("Failed to parse starter dependency config version")
  }

  internal fun mergeDependencyConfigs(dependencyConfig: DependencyConfig, dependencyConfigUpdates: DependencyConfig?): DependencyConfig {
    if (dependencyConfigUpdates == null ||
        dependencyConfig.version.toFloat() > dependencyConfigUpdates.version.toFloat()) return dependencyConfig

    val newVersion = dependencyConfigUpdates.version

    val properties = (dependencyConfig.properties.keys union dependencyConfigUpdates.properties.keys).associateWith { propertyKey ->
      dependencyConfigUpdates.properties[propertyKey] ?: dependencyConfig.properties[propertyKey]
      ?: error("Failed to find property value for key: $propertyKey")
    }

    val dependencies = dependencyConfig.dependencies.map { dependency ->
      val newDependencyVersion = (dependencyConfigUpdates.dependencies.find { updatedDependency ->
        dependency.group == updatedDependency.group && dependency.artifact == updatedDependency.artifact
      }?.version ?: dependency.version).let {
        interpolateDependencyVersion(dependency.group, dependency.artifact, it, properties, true)
      }

      Dependency(dependency.group, dependency.artifact, newDependencyVersion)
    }

    return DependencyConfig(newVersion, properties, dependencies)
  }

  internal fun isDependencyUpdateFileExpired(file: File): Boolean {
    val lastModifiedMs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastModifiedTime().toMillis()
    val lastModified = Instant.ofEpochMilli(lastModifiedMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return lastModified.isBefore(LocalDate.now().atStartOfDay())
  }

  private fun interpolateDependencyVersion(groupId: String, artifactId: String, version: String,
                                           properties: Map<String, String>, interpolateProperties: Boolean = true): String {
    val versionMatch = PLACEHOLDER_VERSION_PATTERN.matchEntire(version)
    if (versionMatch != null) {
      val propertyName = versionMatch.groupValues[1]
      val propertyValue = properties[propertyName]
      if (propertyValue.isNullOrEmpty()) {
        throw IncorrectBomFileException("No such property '${propertyName}' for version of '${groupId}:${artifactId}'")
      }
      return if (interpolateProperties) propertyValue else version
    }
    return version
  }
}