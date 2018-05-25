// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import java.io.File
import java.io.FileInputStream
import java.util.*

object KotlinTestProperties {
  private val pathToProperties: String
    get() = System.getenv("kotlin.gui.test.properties.file") ?: throw IllegalStateException("Property `kotlin.gui.test.properties.file` not set in the Environment!")
  private val props by lazy {
    val propertiesFromFile = Properties()
    val propertiesFile = File(pathToProperties)
    if(propertiesFile.exists()) {
      val input = FileInputStream(propertiesFile)
      propertiesFromFile.load(input)
      input.close()
    }
    propertiesFromFile
  }

  private fun getPropertyValue(propertyName: String, defaultValue: String? = null): String =
    props.getProperty(propertyName, defaultValue) ?: System.getenv(propertyName) ?:
    throw IllegalStateException("Property `$propertyName` not set either in Environment or in `kotlin.gui.test.properties` file!")

  /**
   * kotlin artifact name as it should be present
   * in the variable `kotlin_version`
   */
  private val art_version_from_env = getPropertyValue(
    "kotlin.artifact.version", kotlin_plugin_version_main)
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
   * @return true - if artifact with version specified by kotlin_artifact_version is uploaded to the bintray, so no additional repositories are required
   */
  val isArtifactOnlyInDevRep: Boolean
    get() = getPropertyValue("kotlin.artifact.isOnlyDevRep", "true").toBoolean()

   /**
   * @return kotlin plugin version it can differ from kotlin artifact version in case of developed versions. e.g. `1.2.41-release` or `1.2.50-dev-1065`
   */
  val kotlin_plugin_version_main: String
    get() = getPropertyValue("kotlin.plugin.version.main")

  /**
   * @return the full name (including folders) of the kotlin IDE plugin zip file
   */
  val kotlin_plugin_install_path: String
    get() = getPropertyValue("kotlin.plugin.install.path")

  /**
   * @return Kotlin plugin version with IDE marker, e.g. `1.2.41-release-IJ2018.2-1`. This value is shown in Plugins dialog.
   */
  val kotlin_plugin_version_full: String
    get() = getPropertyValue("kotlin.plugin.version.full")

  /**
   * @return path where the Java is installed
   */
  val jdk_path: String
    get() = getPropertyValue("JAVA_HOME")

}