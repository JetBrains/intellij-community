package com.intellij.tools.launch

import java.io.File
import java.util.*

object TeamCityHelper {

  val systemProperties: Map<String, String> by lazy {
    if (!isUnderTeamCity) {
      return@lazy mapOf<String, String>()
    }

    val systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE"

    val systemPropertiesFile = System.getenv(systemPropertiesEnvName)
    if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
      throw RuntimeException("TeamCity environment variable $systemPropertiesEnvName was not found while running under TeamCity")
    }

    val file = File(systemPropertiesFile)
    if (!file.exists()) {
      throw RuntimeException("TeamCity system properties file is not found: $file")
    }

    return@lazy loadPropertiesFile(file)
  }

  val isUnderTeamCity: Boolean by lazy {
    val version = System.getenv("TEAMCITY_VERSION")
    if (version != null) {
      println("TeamCityHelper: running under TeamCity $version")
    }
    else {
      println("TeamCityHelper: NOT running under TeamCity")
    }
    version != null
  }

  val tempDirectory: File? by lazy {
    systemProperties["teamcity.build.tempDir"]?.let { File(it) }
  }

  private fun loadPropertiesFile(file: File): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val properties = Properties()
    file.reader(Charsets.UTF_8).use { properties.load(it) }

    for (entry in properties.entries) {

      result[entry.key as String] = entry.value as String
    }
    return result
  }
}