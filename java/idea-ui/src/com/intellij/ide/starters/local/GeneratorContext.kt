// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile

internal class GeneratorContext(
  val starterId: String,
  val moduleName: String,
  val group: String,
  val artifact: String,
  val version: String,
  val testRunnerId: String?,
  val rootPackage: String,
  val sdkVersion: JavaSdkVersion?,
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
  fun asPlaceholder(propertyId: String): String {
    return "\${$propertyId}"
  }

  fun isSdkAtLeast(version: String): Boolean {
    val desiredSdkVersion = JavaSdkVersion.fromVersionString(version)
    return desiredSdkVersion != null && sdkVersion != null && sdkVersion.isAtLeast(desiredSdkVersion)
  }

  val rootPackagePath: String
    get() {
      return rootPackage.replace(".", "/").removeSuffix("/")
    }

  val sdkFeatureVersion: Int
    get() {
      return sdkVersion?.maxLanguageLevel?.toJavaVersion()?.feature ?: 8
    }
}