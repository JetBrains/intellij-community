// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.loadPropertiesFile
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
object TeamCityHelper {
  @JvmField
  val isUnderTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null

  @JvmField
  val isPersonalBuild: Boolean = System.getenv("BUILD_IS_PERSONAL") == "true"

  val checkoutDirectory: Path?
    get() {
      if (!isUnderTeamCity) {
        return null
      }

      val name = "teamcity.build.checkoutDir"
      val value = systemProperties[name]
      if (value.isNullOrEmpty()) {
        throw RuntimeException("TeamCity system property " + name + "was not found while running under TeamCity")
      }

      val file = Path.of(value)
      if (!Files.isDirectory(file)) {
        throw RuntimeException("TeamCity system property $name contains non existent directory: $file")
      }

      return file
    }

  val persistentCachePath: Path?
    get() = (systemProperties["agent.persistent.cache"] ?: System.getProperty("agent.persistent.cache"))?.let { Path.of(it) }

  val tempDirectory: Path?
    get() {
      val systemProperties = systemProperties
      if (systemProperties.isEmpty()) {
        return null
      }
      val propertyName = "teamcity.build.tempDir"
      val tempPath = systemProperties[propertyName]
                     ?: throw IllegalStateException("TeamCity must provide system property $propertyName")
      return Path.of(tempPath)
    }

  val systemProperties: Map<String, String> by lazy {
    if (!isUnderTeamCity) {
      return@lazy emptyMap<String, String>()
    }
    val systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE"
    val systemPropertiesFile = System.getenv(systemPropertiesEnvName)
    if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
      throw RuntimeException("TeamCity environment variable $systemPropertiesEnvName was not found while running under TeamCity")
    }
    val file = Path.of(systemPropertiesFile)
    if (!Files.exists(file)) {
      throw RuntimeException("TeamCity system properties file is not found: $file")
    }
    loadPropertiesFile(file)
  }

  val allProperties: Map<String, String> by lazy {
    if (!isUnderTeamCity) {
      return@lazy HashMap<String, String>()
    }
    val propertyName = "teamcity.configuration.properties.file"
    val value = systemProperties[propertyName]
    if (value.isNullOrEmpty()) {
      throw RuntimeException("TeamCity system property '$propertyName is not found")
    }
    val file = Path.of(value)
    if (!Files.exists(file)) {
      throw RuntimeException("TeamCity configuration properties file was not found: $file")
    }
    loadPropertiesFile(file)
  }
}
