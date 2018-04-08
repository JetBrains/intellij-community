// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import java.io.File
import java.io.FileInputStream
import java.util.*

object KotlinTestProperties {
  const val PATH_TO_CONFIG = "com/intellij/ide/projectWizard/kotlin/kotlin.gui.test.properties"
  private val props by lazy {
    val propertiesFromFile = Properties()
    val propertiesUri = this.javaClass.classLoader.getResource(PATH_TO_CONFIG)?.toURI()
    if(propertiesUri != null) {
      val input = FileInputStream(File(propertiesUri))
      propertiesFromFile.load(input)
      input.close()
    }
    propertiesFromFile
  }

  private fun getPropertyValue(propertyName: String, defaultValue: String? = null): String =
    System.getenv(propertyName) ?: props.getProperty(propertyName, defaultValue) ?:
    throw IllegalStateException("Property `$propertyName` not set either in Environment or in `kotlin.gui.test.properties` file!")

  /**
   * kotlin artifact name as it should be present
   * in the variable `kotlin_version`
   */
  private val art_version_from_env = getPropertyValue(
    "kotlin.artifact.version")
  private var art_version_from_test: String? = null
  var kotlin_artifact_version: String
    set(value) {
      art_version_from_test = value
    }
    get() = art_version_from_test
            ?: art_version_from_env

  fun useKotlinArtifactFromEnvironment(){
    art_version_from_test = null
  }

  fun isActualKotlinUsed() = art_version_from_test == null
  /**
   * @return true - if artifact with version specified by kotlin_artifact_version is present in the `Configure Kotlin` dialog
   */
  val isArtifactPresentInConfigureDialog: Boolean
    get() = getPropertyValue("kotlin.artifact.isPresentInConfigureDialog",
                                                                                              "true").toBoolean()

  /**
   * @return true - if artifact with version specified by kotlin_artifact_version is present in the `Configure Kotlin` dialog
   */
  val isArtifactConfiguredManually: Boolean
    get() = getPropertyValue("kotlin.artifact.isConfiguredManually",
                                                                                              "false").toBoolean()

  /**
   * @return true - if artifact with version specified by kotlin_artifact_version is uploaded to the bintray, so no additional repositories are required
   */
  val isArtifactOnlyInDevRep: Boolean
    get() = getPropertyValue("kotlin.artifact.isOnlyDevRep", "true").toBoolean()

  /**
   * @return true - projects created during tests working should be removed
   */
  val kotlin_projects_remove: Boolean
    get() = getPropertyValue("kotlin.projects.remove", "true").toBoolean()

  /**
   * @return kotlin plugin version it can differ from kotlin artifact version in case of developed versions
   */
  val kotlin_plugin_version_main: String
    get() = getPropertyValue("kotlin.plugin.version.main")

  /**
   * @return folder on the local file system where the zip file with kotlin plugin is located
   */
  val kotlin_plugin_download_path: String
    get() = getPropertyValue("kotlin.plugin.download.path")

  /**
   * @return rough version of IDE with IJ/AS prefix what the kotlin plugin is tested against
   */
  val ide_tested: String
    get() = getPropertyValue("ide.tested", "IJ2017.3")

  /**
   * @return last number of kotlin plugin version (or patch version), this number goes after ide codename
   */
  val kotlin_plugin_version_micro: String
    get() = getPropertyValue("kotlin.plugin.version.micro", "1")

  /**
   * @return path where the Java is installed
   */
  val jdk_path: String
    get() = getPropertyValue("JAVA_HOME")

}