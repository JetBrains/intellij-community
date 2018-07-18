// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.*
import org.junit.Test

class CreateKotlinMPProjectGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("kotlin_mpp_common_root")
  fun createKotlinMppProjectCommonRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = MppProjectStructure.RootCommonModule,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      kotlinKind = KotlinKind.Common
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false, KotlinKind.JVM, "$projectName-jvm")
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false, KotlinKind.JS, "$projectName-js")
    }
    gradleReimport()
    waitAMoment(extraTimeOut)

    val expectedJars = (kotlinLibs[KotlinKind.Common]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JVM)) kotlinLibs[KotlinKind.JVM]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList()) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JS)) kotlinLibs[KotlinKind.JS]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList())
      ).toSet()

    checkInProjectStructure {
      checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )
      checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "$projectName", "${projectName}_main", "Kotlin")
      checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "$projectName", "${projectName}_test", "Kotlin")
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_main", "Kotlin")
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_test", "Kotlin")
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_main", "Kotlin")
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_test", "Kotlin")
      }
    }
  }

  @Test
  @JvmName("kotlin_mpp_empty_root")
  fun createKotlinMppProjectEmptyRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = MppProjectStructure.RootEmptyModule,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      kotlinKind = KotlinKind.Common,
      projectName = *arrayOf("$projectName-common")
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false, KotlinKind.JVM,  "$projectName-jvm")
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false, KotlinKind.JS,  "$projectName-js")
    }
    gradleReimport()
    waitAMoment(extraTimeOut)

    val expectedJars = (kotlinLibs[KotlinKind.Common]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JVM)) kotlinLibs[KotlinKind.JVM]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList()) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JS)) kotlinLibs[KotlinKind.JS]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList())
      ).toSet()

    checkInProjectStructure {
      checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )
      checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "${projectName}-common", "${projectName}-common_main", "Kotlin")
      checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "${projectName}-common", "${projectName}-common_test", "Kotlin")
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_main", "Kotlin")
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_test", "Kotlin")
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_main", "Kotlin")
        checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_test", "Kotlin")
      }
    }
  }
}