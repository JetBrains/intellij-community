// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import java.io.Serializable

const val KOTLIN_PLUGIN_NAME = "Kotlin"

enum class ProjectStructure { Java, Kotlin, Gradle, Maven }
enum class BuildSystem { Gradle, Maven, IDEA }

typealias ArtifactType = Map<String, List<String>>

fun ArtifactType.getJars(kotlinVersion: String) = this.keys.sortedDescending()
                                                    .firstOrNull { kotlinVersion >= it }
                                                    ?.let { this[it]!! }
                                                  ?: emptyList()

operator fun ArtifactType.plus(right: ArtifactType): ArtifactType =
  (keys + right.keys).asSequence().sorted().map {
    Pair(it, (getJars(it) + right.getJars(it)).asSequence().toSet().toList())
  }.toMap()

data class ProjectProperties(
  val group: NewProjectDialogModel.Groups, // on the New Project dialog
  val frameworkName: String, // on the New Project dialog
  val isKotlinDsl: Boolean = false, // on the New Project dialog, only for Gradle group
  // for Gradle and Maven based projects: shown in the Project Structure dialog, tab Libraries
  // for Idea based projects: list of jar files containing the library specified by libName property
  val jars: ArtifactType,
  // for Gradle and Maven based projects: null (not used)
  // for Idea based projects: shown in the Project Structure dialog, tab Libraries
  val libName: String? = null,
  // Idea based projects: not used
  // Gradle/Maven based projects: used for multimodule projects
  val modules: Set<TargetPlatform> = emptySet(),
  // Idea, Gradle or Maven
  val buildSystem: BuildSystem,
  // project SDK
  val projectSdk: String = "1.8"
) : Serializable

enum class Projects(val title: String) {
  JavaProject("javaProject"),
  KotlinProjectJvm("kotlinProjectJvm"),
  KotlinProjectJs("kotlinProjectJs"),
  KotlinProjectNative("kotlinProjectNative"),
  KotlinMPProjectLibrary("kotlinMPProjectLibrary"),
  KotlinMPProjectClientServer("kotlinMPProjectClientServer"),
  KotlinMPProjectMobileAndroidIos("KotlinMPProjectMobileAndroidIos"),
  KotlinMPProjectMobileLibrary("KotlinMPProjectMobileLibrary"),
  GradleGProjectJvm("gradleGProjectJvm"),
  GradleGProjectJs("gradleGProjectJs"),
  GradleKProjectJvm("gradleKProjectJvm"),
  GradleKProjectJs("gradleKProjectJs"),
  MavenProjectJvm("mavenProjectJvm"),
  MavenProjectJs("mavenProjectJs"),
  ;

  override fun toString() = title
}

val kotlinProjects = mapOf(
  Projects.JavaProject to ProjectProperties(
    group = NewProjectDialogModel.Groups.Java,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinJvm,
    libName = "KotlinJavaRuntime",
    jars = kotlinJvmJavaKotlinJars,
    buildSystem = BuildSystem.IDEA
  ),
  Projects.KotlinProjectJvm to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemGradleKotlinJvm,
    libName = "KotlinJavaRuntime",
    jars = kotlinJvmJavaKotlinJars,
    buildSystem = BuildSystem.IDEA
  ),
  Projects.KotlinProjectJs to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemGradleKotlinJs,
    libName = "KotlinJavaScript",
    jars = kotlinJsJavaKotlinLibs,
    buildSystem = BuildSystem.IDEA
  ),
  Projects.KotlinProjectNative to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinNative,
    modules = setOf(TargetPlatform.Native),
    jars = kotlinNativeMpp,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.KotlinMPProjectLibrary to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinMppLibrary,
    modules = setOf(TargetPlatform.JVM16, TargetPlatform.Common, TargetPlatform.JavaScript, TargetPlatform.Native),
    jars = kotlinJvmMppGradle + kotlinJsGradleLibs + kotlinNativeMpp + kotlinCommonMpp,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.KotlinMPProjectClientServer to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinMppClientServer,
    modules = setOf(TargetPlatform.JVM16, TargetPlatform.Common, TargetPlatform.JavaScript),
    jars = kotlinJvmMppGradle + kotlinJsGradleLibs + kotlinCommonMpp,
    buildSystem = BuildSystem.Gradle
  ),
  // Android project structure!
  Projects.KotlinMPProjectMobileAndroidIos to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinMppMobileAndroidIos,
    modules = setOf(TargetPlatform.Native, TargetPlatform.Common),
    jars = kotlinNativeMpp + kotlinCommonMpp,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.KotlinMPProjectMobileLibrary to ProjectProperties(
    group = NewProjectDialogModel.Groups.Kotlin,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinMppMobileSharedLibrary,
    modules = setOf(TargetPlatform.JVM16, TargetPlatform.Common, TargetPlatform.Native),
    jars = kotlinJvmMobileLibrary + kotlinCommonMpp + kotlinNativeMpp,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.GradleGProjectJvm to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinJvm,
    modules = setOf(TargetPlatform.JVM18),
    jars = kotlinJvmGradleLibs,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.GradleGProjectJs to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinJs,
    modules = setOf(TargetPlatform.JavaScript),
    // TODO: change back to kotlinJsGradleLibs after KT-21166 fixing
    jars = kotlinJsGradleKLibs,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.GradleKProjectJvm to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinJvm,
    isKotlinDsl = true,
    modules = setOf(TargetPlatform.JVM18),
    jars = kotlinJvmGradleLibs,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.GradleKProjectJs to ProjectProperties(
    group = NewProjectDialogModel.Groups.Gradle,
    frameworkName = NewProjectDialogModel.Constants.itemKotlinJs,
    isKotlinDsl = true,
    modules = setOf(TargetPlatform.JavaScript),
    jars = kotlinJsGradleKLibs,
    buildSystem = BuildSystem.Gradle
  ),
  Projects.MavenProjectJvm to ProjectProperties(
    group = NewProjectDialogModel.Groups.Maven,
    frameworkName = "kotlin-archetype-jvm",
    modules = setOf(TargetPlatform.JVM16),
    jars = kotlinJvmMavenLibs,
    buildSystem = BuildSystem.Maven
  ),
  Projects.MavenProjectJs to ProjectProperties(
    group = NewProjectDialogModel.Groups.Maven,
    frameworkName = "kotlin-archetype-js",
    modules = setOf(TargetPlatform.JavaScript),
    jars = kotlinJsMavenLibs,
    buildSystem = BuildSystem.Maven
  )
)

/**
 * Relative path to jar files within kotlin plugin
 * */
val pathKotlinInConfig = "/plugins/Kotlin/kotlinc/lib".normalizeSeparator()

data class ProjectRoots(val sourceRoot: String, val testRoot: String) : Serializable

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
  TargetPlatform.Native to FacetStructure(
    targetPlatform = TargetPlatform.Native,
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
