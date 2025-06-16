// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.text.SemVer
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.libraries.isLibraryModule
import org.jetbrains.intellij.build.impl.maven.GeneratedMavenArtifacts
import org.jetbrains.intellij.build.impl.maven.MavenCentralPublication
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import kotlin.io.path.exists
import kotlin.io.path.name

internal object JewelMavenArtifacts {
  private const val GROUP_ID: String = "org.jetbrains.jewel"
  private val CORE: Map<String, String> = mapOf(
    "intellij.platform.jewel.foundation" to "jewel-foundation",
    "intellij.platform.jewel.markdown.core" to "jewel-markdown-core",
    "intellij.platform.jewel.ui" to "jewel-ui",
    "intellij.platform.jewel.markdown.extensions.gfmTables" to "jewel-markdown-extensions-gfm-tables",
    "intellij.platform.jewel.markdown.extensions.gfmStrikethrough" to "jewel-markdown-extensions-gfm-strikethrough",
    "intellij.platform.jewel.markdown.extensions.autolink" to "jewel-markdown-extensions-autolink",
    "intellij.platform.jewel.markdown.extensions.gfmAlerts" to "jewel-markdown-extensions-gfm-alerts",
  )
  val STANDALONE: Map<String, String> = mapOf(
    "intellij.platform.jewel.markdown.intUiStandaloneStyling" to "jewel-markdown-int-ui-standalone-styling",
    "intellij.platform.jewel.intUi.decoratedWindow" to "jewel-int-ui-decorated-window",
    "intellij.platform.jewel.intUi.standalone" to "jewel-int-ui-standalone",
    "intellij.platform.jewel.decoratedWindow" to "jewel-decorated-window",
  )

  private val ALL: Map<String, String> = CORE + STANDALONE
  val ALL_MODULES: Set<String> = ALL.keys

  private val VERSION: String by lazy {
    val jewelProperties = COMMUNITY_ROOT.communityRoot.resolve("platform/jewel/gradle.properties")
    check(jewelProperties.exists()) { "$jewelProperties is missing" }
    DependenciesProperties(COMMUNITY_ROOT, jewelProperties).property("jewel.release.version")
  }

  fun isJewel(module: JpsModule): Boolean {
    return module.name.startsWith("intellij.platform.jewel.")
  }

  fun patchCoordinates(module: JpsModule, coordinates: MavenCoordinates): MavenCoordinates {
    check(isJewel(module))
    val version = "$VERSION-${coordinates.version}"
    val patched = coordinates.copy(groupId = GROUP_ID, version = version)
    checkNotNull(SemVer.parseFromText(version)) {
      "$patched is expected to match the Semantic Versioning, see https://semver.org"
    }
    return patched
  }

  fun addPomMetadata(module: JpsModule, model: Model) {
    check(isJewel(module))
    model.name = "Jewel"
    model.description = "A theme for Compose for Desktop that implements the IntelliJ Platform look and feel."
    model.url = "https://github.com/JetBrains/intellij-community"
    model.addLicense(License().apply {
      name = "Apache License 2.0"
      url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    })
    model.scm = Scm().apply {
      connection = "scm:git:https://github.com/JetBrains/intellij-community.git"
      developerConnection = "scm:git:https://github.com/JetBrains/intellij-community.git"
      url = "https://github.com/JetBrains/intellij-community"
    }
    model.addDeveloper(Developer().apply {
      id = "Google"
      name = "Google Team"
      organization = "Google"
      organizationUrl = "https://developer.android.com"
    })
  }

  private fun JpsModule.modulesTree(): Sequence<JpsModule> {
    return sequenceOf(this) + dependenciesList.dependencies.asSequence()
      .filterIsInstance<JpsModuleDependency>()
      .mapNotNull { it.module }
      .flatMap { it.modulesTree() }
  }

  fun validate(context: BuildContext, mavenArtifacts: Collection<GeneratedMavenArtifacts>) {
    ALL_MODULES.asSequence()
      .map(context::findRequiredModule)
      .flatMap { it.modulesTree() }
      .distinct().forEach { module ->
        val artifact = mavenArtifacts.singleOrNull { (it) -> it.name == module.name }
        if (module.isLibraryModule()) {
          check(artifact == null) {
            "Maven artifact for the library module ${module.name} is not supposed to be created: $artifact"
          }
        }
        else {
          checkNotNull(artifact) {
            "No maven artifact is created for the module ${module.name}:\n$mavenArtifacts"
          }
          check(artifact.coordinates.groupId == GROUP_ID) {
            "The module ${module.name} has groupId=${artifact.coordinates.groupId} " +
            "but it's expected to have groupId=$GROUP_ID because Maven Central publication credentials are issues per namespace/groupId"
          }
          validateForMavenCentralPublication(artifact)
        }
      }
    for ((jewelModuleName, artifactId) in ALL) {
      check(mavenArtifacts.any { (module, mavenCoordinates) ->
        module.name == jewelModuleName &&
        mavenCoordinates.groupId == GROUP_ID &&
        mavenCoordinates.artifactId == artifactId
      }) {
        "The module $jewelModuleName is expected to have groupId=$GROUP_ID and artifactId=$artifactId: " +
        "${mavenArtifacts.filter { (module) -> module.name == jewelModuleName }}"
      }
    }
  }

  /**
   * See https://central.sonatype.org/publish/requirements
   */
  private fun validateForMavenCentralPublication(artifacts: GeneratedMavenArtifacts) {
    val sources = artifacts.coordinates.getFileName("sources", "jar")
    check(artifacts.files.any { it.name == sources }) {
      "No $sources is generated for the module ${artifacts.module.name}"
    }
    val javadoc = artifacts.coordinates.getFileName("javadoc", "jar")
    check(artifacts.files.any { it.name == javadoc }) {
      "No $javadoc is generated for the module ${artifacts.module.name}"
    }
    val pom = artifacts.coordinates.getFileName(packaging = "pom")
    val pomXml = artifacts.files.singleOrNull { it.name == pom }
    check(pomXml != null) {
      "No $pom is generated for the module ${artifacts.module.name}"
    }
    val coordinates = MavenCentralPublication.loadAndValidatePomXml(pomXml)
    check(coordinates == artifacts.coordinates) {
      "Maven coordinates ${artifacts.coordinates} generated for the module ${artifacts.module.name} " +
      "don't match the coordinates $coordinates from $pomXml"
    }
  }
}


