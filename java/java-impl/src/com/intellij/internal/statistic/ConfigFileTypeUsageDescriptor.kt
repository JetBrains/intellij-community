// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.java.library.JavaLibraryUtil.hasLibraryJar
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile

private const val APPLICATION_PROPERTIES: String = "application.properties"
private const val APPLICATION_YAML: String = "application.yaml"
private const val APPLICATION_YML: String = "application.yml"

private val EXTENSIONS = listOf("yml", "yaml", "properties")
private const val APPLICATION_PREFIX = "application-"

private const val SPRING_BOOT_MAVEN: String = "org.springframework.boot:spring-boot"
private const val MICRONAUT_MAVEN: String = "io.micronaut:micronaut-core"
private const val QUARKUS_MAVEN: String = "io.quarkus:quarkus-core"
private const val KTOR_MAVEN: String = "io.ktor:ktor-http"

private val ALL_MAVEN: Collection<String> = listOf(
  SPRING_BOOT_MAVEN,
  MICRONAUT_MAVEN,
  QUARKUS_MAVEN,
  KTOR_MAVEN,
)

private val SCHEMA_KEY: Key<String> = Key.create("JVM_FRAMEWORK_CONFIG")

internal class SpringFileTypeUsageDescriptor : ConfigFileTypeUsageDescriptor(SPRING_BOOT_MAVEN)
internal class MicronautFileTypeUsageDescriptor : ConfigFileTypeUsageDescriptor(MICRONAUT_MAVEN)
internal class QuarkusFileTypeUsageDescriptor : ConfigFileTypeUsageDescriptor(QUARKUS_MAVEN)
internal class KtorFileTypeUsageDescriptor : ConfigFileTypeUsageDescriptor(KTOR_MAVEN)

internal open class ConfigFileTypeUsageDescriptor(private val libraryMaven: String) : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    if (!isApplicationConfig(file.name)) return false

    file.getUserData(SCHEMA_KEY)?.let { return it == libraryMaven }

    val mavenCoords = ReadAction.nonBlocking<String> {
      val module = try {
        ModuleUtilCore.findModuleForFile(file, project)
      }
      catch (ignored: IndexNotReadyException) {
        null
      }

      ALL_MAVEN.find { hasLibraryJar(module, it) } ?: ""
    }.executeSynchronously()

    file.putUserData(SCHEMA_KEY, mavenCoords)

    return mavenCoords == libraryMaven
  }

  private fun isApplicationConfig(fileName: @NlsSafe String): Boolean {
    return fileName == APPLICATION_PROPERTIES
           || fileName == APPLICATION_YAML
           || fileName == APPLICATION_YML
           || fileName.startsWith(APPLICATION_PREFIX) && FileUtilRt.getExtension(fileName) in EXTENSIONS
  }
}