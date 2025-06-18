// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.SystemPropertiesHelper.getMavenOptsEnvVariable
import org.jetbrains.intellij.build.SystemPropertiesHelper.getUserHome
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getChildElements
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists

/**
 * [https://maven.apache.org/configure.html](https://maven.apache.org/configure.html)
 */
private const val MAVEN_OPTS = "MAVEN_OPTS"

/**
 * [https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html](https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html#org.apache.maven.api.Constants.MAVEN_REPO_LOCAL)
 */
private const val MAVEN_REPO_LOCAL: String = "maven.repo.local"

private val LOG = Logger.getLogger("MavenRepositoryPathUtil")

private val defaultMavenHome get() = Path.of(getUserHome(), ".m2")

/**
 * Determines the Maven repository path by checking several locations:
 * 1. MAVEN_OPTS environment variable with maven.repo.local parameter
 * 2. localRepository settings in ~/.m2/settings.xml
 * 3. Default location at ~/.m2/repository
 *
 * Equivalent to `org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath()`.
 *
 * @return Path to the Maven repository as String
 */
fun getMavenRepositoryPath(): String {
  return findMavenRepositoryProperty(getMavenOptsEnvVariable())?.takeIf { it.isNotBlank() }
           ?.also { LOG.info("Maven repository path is read from MAVEN_OPTS environment variable: $it") }

         ?: findMavenSettingsPath()?.takeIf { it.exists() }?.run {
           getRepositoryFromSettings()
             ?.also { repo -> LOG.info("Maven repository path is read from $this - $repo") }
         }

         ?: defaultMavenHome.resolve("repository").toString()
}

private fun findMavenRepositoryProperty(mavenOpts: String?): String? {
  mavenOpts ?: return null

  // -Dmaven.repo.local=/path/to/repo     -> [1]:"maven.repo.local" [2]:"" [3]:"/path/to/repo"
  // -Dmaven.repo.local="/my custom/path" -> [1]:"maven.repo.local" [2]:"/my custom/path" [3]:""
  val propertyRegex = Regex("""-D([^=\s]+)(?:=(?:"([^"]+)"|(\S+)))?""")
  val properties = propertyRegex.findAll(mavenOpts).associate { property ->
    property.groupValues[1] to (property.groupValues[2].takeIf { it.isNotBlank() } ?: property.groupValues[3])
  }
  return properties[MAVEN_REPO_LOCAL]
}

private fun findMavenSettingsPath(): Path? {
  val userSettings = defaultMavenHome.resolve("settings.xml")
  if (userSettings.exists()) return userSettings

  fromBrew()?.getSettingsFileFromHome()?.takeIf { it.exists() }?.let {
    LOG.info("Maven home is read from brew: $it")
    return it
  }

  val defaultGlobalPath = "/usr/share/maven"
  if (!isWindows) defaultGlobalPath.getSettingsFileFromHome().takeIf { it.exists() }?.let {
    LOG.info("Maven home is read from $defaultGlobalPath")
    return it
  }
  return null
}

private fun Path.getRepositoryFromSettings(): String? = runCatching {
  val builder = DocumentBuilderFactory.newInstance()
    .apply { isNamespaceAware = true }
    .newDocumentBuilder()

  builder.parse(this.toUri().toString())
    .documentElement
    .getChildElements("localRepository")
    .firstOrNull()
    ?.textContent
}.getOrNull()

private fun String.getSettingsFileFromHome(): Path = Path.of(this, "conf", "settings.xml")

private fun fromBrew(): String? {
  if (!isMac) return null
  val defaultBrewPath = "/opt/homebrew/Cellar/maven/Current/libexec"
  return defaultBrewPath.takeIf { Files.exists(Path.of(it)) }
}

private val isMac = System.getProperty("os.name").startsWith("mac", ignoreCase = true)
private val isWindows = System.getProperty("os.name").startsWith("windows", ignoreCase = true)

@VisibleForTesting
object SystemPropertiesHelper {
  @JvmStatic
  fun getMavenOptsEnvVariable(): String? = System.getenv(MAVEN_OPTS)

  @JvmStatic
  fun getUserHome(): String = System.getProperty("user.home") ?: ""
}
