// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel

const val KOTLIN_PLUGIN_NAME = "Kotlin"

enum class KotlinKind { JVM, JS, Common }
enum class ProjectStructure { Java, Kotlin, Gradle, Maven }
enum class BuildSystem { Gradle, Maven, IDEA }

typealias ArtifactType = Map<String, List<String>>

fun ArtifactType.getJars(kotlinVersion: String) = this.keys.sortedDescending()
                                                    .firstOrNull { kotlinVersion >= it }
                                                    ?.let { this[it]!! }
                                                  ?: emptyList()

data class ProjectProperties(
  val group: String,
  val frameworkName: String,
  val jars: ArtifactType,
  val libName: String? = null
)

enum class Projects(val title: String) {
  Kind("kind"),
  JavaProject("javaProject"),
  KotlinProject("kotlinProject"),
  KotlinMPProject("kotlinMPProject"),
  GradleGProject("gradleGProject"),
  GradleKProject("gradleKProject"),
  GradleGMPProject("gradleGMPProject"),
  MavenProject("mavenProject"),
  ;

  override fun toString() = title
}

data class KotlinLib(private val map: Map<String, Any?>) {
  val kind: KotlinKind by map
  val javaProject: ProjectProperties by map
  val kotlinProject: ProjectProperties by map
  val kotlinMPProject: ProjectProperties by map
  val gradleGProject: ProjectProperties by map
  val gradleKProject: ProjectProperties by map
  val gradleGMPProject: ProjectProperties by map
  val mavenProject: ProjectProperties by map
}

private val kotlinJvm = KotlinLib(mapOf(
  Projects.Kind.title to KotlinKind.JVM,
  Projects.JavaProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Java.toString(),
    frameworkName = "Kotlin/JVM",
    libName = "KotlinJavaRuntime",
    jars = kotlinJvmJavaKotlinJars
  ),
  Projects.KotlinProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin.toString(),
    frameworkName = "Kotlin/JVM",
    libName = "KotlinJavaRuntime",
    jars = kotlinJvmJavaKotlinJars
  ),
  Projects.KotlinMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin.toString(),
    frameworkName = "Kotlin (Multiplatform - Experimental)",
    jars = kotlinJvmMppKotlin
  ),
  Projects.GradleGProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (Java)",
    jars = kotlinJvmGradleLibs
  ),
  Projects.GradleGMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (Multiplatform JVM - Experimental)",
    jars = kotlinJvmMppGradle
  ),
  Projects.MavenProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Maven.toString(),
    frameworkName = "kotlin-archetype-jvm",
    jars = kotlinJvmMavenLibs
  ),
  Projects.GradleKProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (Java)",
    jars = kotlinJvmGradleLibs
  )
))

private val kotlinJs = KotlinLib(mapOf(
  Projects.Kind.title to KotlinKind.JS,
  Projects.JavaProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Java.toString(),
    frameworkName = "Kotlin/JS",
    libName = "KotlinJavaScript",
    jars = kotlinJsJavaKotlinLibs
  ),
  Projects.KotlinProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin.toString(),
    frameworkName = "Kotlin/JS",
    libName = "KotlinJavaScript",
    jars = kotlinJsJavaKotlinLibs
  ),
  Projects.KotlinMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin.toString(),
    frameworkName = "Kotlin (Multiplatform - Experimental)",
    jars = kotlinJsGradleLibs
  ),
  Projects.GradleGProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (JavaScript)",
    jars = kotlinJsGradleLibs
  ),
  Projects.GradleGMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (Multiplatform JS - Experimental)",
    jars = kotlinJsMavenLibs
  ),
  Projects.MavenProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Maven.toString(),
    frameworkName = "kotlin-archetype-js",
    jars = kotlinJsMavenLibs
  ),
  Projects.GradleKProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (JavaScript)",
    jars = kotlinJsGradleKLibs
  )
))

private val kotlinCommon = KotlinLib(mapOf(
  Projects.Kind.title to KotlinKind.Common,
  Projects.KotlinMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin.toString(),
    frameworkName = "Kotlin (Multiplatform - Experimental)",
    jars = kotlinCommonMpp
  ),
  Projects.GradleGMPProject.title to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle.toString(),
    frameworkName = "Kotlin (Multiplatform Common - Experimental)",
    jars = kotlinCommonMpp
  )
))

val kotlinLibs = mapOf(
  kotlinJvm.kind to kotlinJvm,
  kotlinJs.kind to kotlinJs,
  kotlinCommon.kind to kotlinCommon
)

/**
 * Relative path to jar files within kotlin plugin
 * */
val pathKotlinInConfig = "/plugins/Kotlin/kotlinc/lib".normalizeSeparator()

data class ProjectRoots(val sourceRoot: String, val testRoot: String)

val sourceRoots = mapOf(
  ProjectStructure.Java to ProjectRoots("src",
                                        "test"),
  ProjectStructure.Kotlin to ProjectRoots("src",
                                          "test"),
  ProjectStructure.Gradle to ProjectRoots(
    "src/main/java",
    "src/test/java"),
  ProjectStructure.Maven to ProjectRoots(
    "src/main/java",
    "src/test/java")
)

// TODO: make search of TestData like in other tests. Uncomment and remake
//val KOTLIN_EXTRA_FILES = "kotlin.extra.files"
//const val KOTLIN_EXTRA_FILES = "extra"
//val sampleMainKt = File(KOTLIN_EXTRA_FILES + File.separator + "mainK1.kt")
//val sampleTestKt = File(
//  PathManagerEx.getCommunityHomePath() + PATH_TO_TESTDATA + File.separator + KOTLIN_EXTRA_FILES + File.separator + "testK1.kt")

val versionFromArtifact: LanguageVersion by lazy {
  getVersionFromString(KotlinTestProperties.kotlin_artifact_version)
}
val versionFromPlugin: LanguageVersion by lazy {
  getVersionFromString(KotlinTestProperties.kotlin_plugin_version_main)
}

val defaultFacetSettings = mapOf(
  TargetPlatform.JVM16 to FacetStructure(
    targetPlatform = TargetPlatform.JVM16,
    languageVersion = versionFromArtifact,
    apiVersion = versionFromArtifact,
    jvmOptions = FacetStructureJVM()
  ),
  TargetPlatform.JVM18 to FacetStructure(
    targetPlatform = TargetPlatform.JVM18,
    languageVersion = versionFromArtifact,
    apiVersion = versionFromArtifact,
    jvmOptions = FacetStructureJVM()
  ),
  TargetPlatform.Common to FacetStructure(
    targetPlatform = TargetPlatform.Common,
    languageVersion = versionFromArtifact,
    apiVersion = versionFromArtifact
  ),
  TargetPlatform.JavaScript to FacetStructure(
    targetPlatform = TargetPlatform.JavaScript,
    languageVersion = versionFromArtifact,
    apiVersion = versionFromArtifact,
    jsOptions = FacetStructureJS()
  )
)

object MPPModules {
  fun mppFullSet() = setOf(KotlinKind.Common,
                           KotlinKind.JVM,
                           KotlinKind.JS)

  fun mppJs() = setOf(KotlinKind.Common,
                      KotlinKind.JS)

  fun mppJvm() = setOf(KotlinKind.Common,
                       KotlinKind.JVM)

  fun mppCommonOnly() = setOf(KotlinKind.Common)
}