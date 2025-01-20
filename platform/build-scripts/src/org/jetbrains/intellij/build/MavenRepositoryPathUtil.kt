// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.SystemProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import java.io.File
import java.nio.file.Path

/**
 * [https://maven.apache.org/configure.html](https://maven.apache.org/configure.html)
 */
private const val MAVEN_OPTS = "MAVEN_OPTS"

/**
 * [https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html](https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html#org.apache.maven.api.Constants.MAVEN_REPO_LOCAL)
 */
private const val MAVEN_REPO_LOCAL: String = "maven.repo.local"


/**
 * Determines the Maven repository path by checking several locations:
 * 1. MAVEN_OPTS environment variable with maven.repo.local parameter
 * 2. localRepository settings in ~/.m2/settings.xml
 * 3. Default location at ~/.m2/repository
 *
 * @param span Optional span for tracing/logging repository location details
 * @return Path to the Maven repository
 */
internal fun getMavenRepositoryPath(span: Span? = null): Path {
  val settingsFile = JpsMavenSettings.getUserMavenSettingsXml().takeIf { it.exists() }
                     ?: JpsMavenSettings.getGlobalMavenSettingsXml().takeIf { it?.exists() == true }
  val attributeKey = AttributeKey.stringKey("local maven repository path")

  return findMavenRepositoryProperty(EnvironmentUtil.getValue(MAVEN_OPTS))?.trim()
           ?.let {
             span?.addEvent("Found MAVEN_OPTS system env", Attributes.of(attributeKey, it))
             Path.of(it)
           }

         ?: settingsFile?.getRepositoryFromSettings()
           ?.let {
             span?.addEvent("Found localRepository param in .m2/settings.xml file", Attributes.of(attributeKey, it))
             Path.of(it)
           }

         ?: Path.of(SystemProperties.getUserHome(), ".m2/repository")
}

private fun findMavenRepositoryProperty(mavenOpts: String?): String? {
  mavenOpts ?: return null
  // -Dmaven.repo.local=/path/to/repo     -> [1]:"maven.repo.local" [2]:"" [3]:"/path/to/repo"
  // -Dmaven.repo.local="/my custom/path" -> [1]:"maven.repo.local" [2]:"/my custom/path" [3]:""
  val propertyRegex = Regex("""-D([^=\s]+)(?:=(?:"([^"]+)"|(\S+)))?""")
  val properties = propertyRegex
    .findAll(mavenOpts)
    .associate { property -> property.groupValues[1] to (property.groupValues[2].takeIf { it.isNotBlank() } ?: property.groupValues[3]) }
  return properties[MAVEN_REPO_LOCAL]
}

private fun File.getRepositoryFromSettings(): String? {
  val element = runCatching { JDOMUtil.load(this) }.getOrNull()
  return element?.content?.firstOrNull { (it as? Element)?.name == "localRepository" }?.value
}