@file:JvmName("StarterSettings")

package com.intellij.ide.starters.shared

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.Nls

data class StarterLanguage(
  val id: String,
  @NlsSafe val title: String,
  val languageId: String,
  val isBuiltIn: Boolean = false,
  @NlsSafe val description: String? = null,
)

data class StarterTestRunner(
  val id: String,
  @NlsSafe val title: String,
)

data class StarterProjectType(
  val id: String,
  @NlsSafe val title: String,
  @NlsSafe val description: String? = null,
)

data class StarterAppType(
  val id: String,
  @NlsSafe val title: String,
)

data class StarterAppPackaging(
  val id: String,
  @NlsSafe val title: String,
  @NlsSafe val description: String? = null,
)

data class StarterLanguageLevel(
  val id: String,
  @NlsSafe val title: String,
  /**
   * Version string that can be parsed with [JavaSdkVersion.fromVersionString].
   */
  val javaVersion: String,
)

class CustomizedMessages {
  var projectTypeLabel: @Label String? = null
  var serverUrlDialogTitle: @DialogTitle String? = null
  var dependenciesLabel: @Label String? = null
  var selectedDependenciesLabel: @Label String? = null
  var noDependenciesSelectedLabel: @Label String? = null
  var frameworkVersionLabel: @Label String? = null
}

class StarterWizardSettings(
  val projectTypes: List<StarterProjectType>,
  val languages: List<StarterLanguage>,
  val isExampleCodeProvided: Boolean,
  val isPackageNameEditable: Boolean,
  val languageLevels: List<StarterLanguageLevel>,
  val defaultLanguageLevel: StarterLanguageLevel?,
  val packagingTypes: List<StarterAppPackaging>,
  val applicationTypes: List<StarterAppType>,
  val testFrameworks: List<StarterTestRunner>,
  val customizedMessages: CustomizedMessages?,
  val showProjectTypes: Boolean,
)

class PluginRecommendation(
  val pluginId: String,
  val dependencyIds: List<String>,
) {
  constructor(pluginId: String, vararg dependencyIds: String) : this(pluginId, dependencyIds.toList())
}

interface LibraryInfo {
  @get:NlsSafe
  val title: String
  val description: String?
  val links: List<LibraryLink>
  val isRequired: Boolean
  val isDefault: Boolean
}

class LibraryLink(
  val type: LibraryLinkType,
  @NlsSafe
  val url: String,
  @NlsSafe
  val title: String? = null,
)

const val DEFAULT_MODULE_NAME: String = "demo"
const val DEFAULT_MODULE_GROUP: String = "com.example"
const val DEFAULT_MODULE_ARTIFACT: String = "demo"
const val DEFAULT_MODULE_VERSION: String = "1.0-SNAPSHOT"
const val DEFAULT_PACKAGE_NAME: String = "$DEFAULT_MODULE_GROUP.$DEFAULT_MODULE_ARTIFACT"

val JAVA_STARTER_LANGUAGE: StarterLanguage = StarterLanguage("java", "Java", "JAVA", true)
val KOTLIN_STARTER_LANGUAGE: StarterLanguage = StarterLanguage("kotlin", "Kotlin", "kotlin")
val GROOVY_STARTER_LANGUAGE: StarterLanguage = StarterLanguage("groovy", "Groovy", "Groovy")

val MAVEN_PROJECT: StarterProjectType = StarterProjectType("maven", "Maven")
val GRADLE_PROJECT: StarterProjectType = StarterProjectType("gradle", "Gradle")

val JUNIT_TEST_RUNNER: StarterTestRunner = StarterTestRunner("junit", "JUnit")
val TESTNG_TEST_RUNNER: StarterTestRunner = StarterTestRunner("testng", "TestNG")

open class CommonStarterContext : UserDataHolderBase() {
  var name: String = DEFAULT_MODULE_NAME
  var group: String = DEFAULT_MODULE_GROUP
  var artifact: String = DEFAULT_MODULE_ARTIFACT
  var version: String = DEFAULT_MODULE_VERSION

  var isCreatingNewProject: Boolean = false
  var gitIntegration: Boolean = false

  lateinit var language: StarterLanguage
  var projectType: StarterProjectType? = null
  var applicationType: StarterAppType? = null
  var testFramework: StarterTestRunner? = null
  var includeExamples: Boolean = true
}

const val ARTIFACT_ID_PROPERTY: String = "artifactId"
const val GROUP_ID_PROPERTY: String = "groupId"

sealed class DependencyState

class DependencyUnavailable(
  @Nls(capitalization = Nls.Capitalization.Sentence)
  val message: String?,
  @NlsSafe
  val hint: String? = null,
) : DependencyState()

object DependencyAvailable : DependencyState()