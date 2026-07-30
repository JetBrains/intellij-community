// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.text.SemVer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.plus
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.libraries.isLibraryModule
import org.jetbrains.intellij.build.impl.maven.DependencyScope
import org.jetbrains.intellij.build.impl.maven.GeneratedMavenArtifacts
import org.jetbrains.intellij.build.impl.maven.MavenArtifactDependency
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

private const val GROUP_ID: String = "org.jetbrains.jewel"

/**
 * The IJP fork of kotlinx.coroutines, and the stock artifact it is forked from.
 *
 * The fork shares the `kotlinx.coroutines.*` packages with the stock artifact but has a different groupId, so
 * neither Gradle nor Maven can conflict-resolve the two. Consumers get both jars, and whichever one sorts first
 * on the classpath wins. The fork lags upstream, so when it wins, anything stock added since the fork point is
 * missing: stock 1.11.0 compiles `runBlocking` to `BuildersKt.runBlockingK` via `@JvmName`, the fork has no such
 * method, and every `runBlocking` call in a consumer throws `NoSuchMethodError`.
 *
 * The fork must never reach standalone consumers. See [JewelMavenArtifacts.patchPlatformDependencies].
 */
private const val IJP_COROUTINES_FORK_GROUP: String = "org.jetbrains.intellij.deps.kotlinx"
private const val STOCK_COROUTINES_GROUP: String = "org.jetbrains.kotlinx"

/** The fork appends this to the stock version it is based on, e.g. `1.10.2-intellij-1` for stock `1.10.2`. */
private const val IJP_COROUTINES_FORK_VERSION_SUFFIX: String = "-intellij"

/**
 * Artifact ID prefix used by the upstream coroutines artifacts and by the IJP fork. Matching on it rather than on
 * [IJP_COROUTINES_FORK_GROUP] also covers a fork that moves to another groupId. This is a best-effort guard: a fork
 * published under an unrelated artifact ID would still get through.
 */
private const val COROUTINES_ARTIFACT_PREFIX: String = "kotlinx-coroutines"

/**
 * Each entry represents a prefix for Platform dependencies which are being published to Maven Central.
 * And distinct Maven Central publication credentials are issued per namespace/groupId.
 * Please do not edit this list since every entry requires a corresponding change on the JetBrains infrastructure.
 */
private val PLATFORM_DEPENDENCY_PREFIXES: Set<String> = setOf(
  "com.jetbrains.intellij.platform:icons-",
)

private val JEWEL_STANDALONE_REQUIRED_ICONS_MODULES: Set<String> = setOf(
  "intellij.platform.icons.api",
  "intellij.platform.icons.api.rendering",
  "intellij.platform.icons.impl",
)

private val CORE: PersistentMap<String, String> = persistentHashMapOf(
  "intellij.platform.jewel.foundation" to "jewel-foundation",
  "intellij.platform.jewel.markdown.core" to "jewel-markdown-core",
  "intellij.platform.jewel.ui" to "jewel-ui",
  "intellij.platform.jewel.markdown.extensions.gfmTables" to "jewel-markdown-extensions-gfm-tables",
  "intellij.platform.jewel.markdown.extensions.gfmStrikethrough" to "jewel-markdown-extensions-gfm-strikethrough",
  "intellij.platform.jewel.markdown.extensions.autolink" to "jewel-markdown-extensions-autolink",
  "intellij.platform.jewel.markdown.extensions.gfmAlerts" to "jewel-markdown-extensions-gfm-alerts",
  "intellij.platform.jewel.markdown.extensions.images" to "jewel-markdown-extensions-images",
)

private val NOT_PUBLISHED: Set<String> = setOf(
  "intellij.platform.jewel.ideLafBridge",
  "intellij.platform.jewel.markdown.ideLafBridgeStyling",
)

private val transitiveJewelDependencies = persistentHashMapOf(
  "jewel-foundation" to emptySet(),
  "jewel-ui" to emptySet(),
  "jewel-decorated-window" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-markdown-core" to setOf("jewel-foundation"),
  "jewel-markdown-extensions-autolink" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-markdown-extensions-gfm-alerts" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-markdown-extensions-gfm-strikethrough" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-markdown-extensions-gfm-tables" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-markdown-extensions-images" to setOf("jewel-foundation", "jewel-ui"),
  "jewel-int-ui-standalone" to setOf("jewel-foundation"),
  "jewel-int-ui-decorated-window" to setOf("jewel-foundation", "jewel-ui", "jewel-int-ui-standalone"),
  "jewel-markdown-int-ui-standalone-styling" to setOf("jewel-foundation", "jewel-ui"),
)

internal object JewelMavenArtifacts {
  internal val STANDALONE: PersistentMap<String, String> = persistentHashMapOf(
    "intellij.platform.jewel.markdown.intUiStandaloneStyling" to "jewel-markdown-int-ui-standalone-styling",
    "intellij.platform.jewel.intUi.decoratedWindow" to "jewel-int-ui-decorated-window",
    "intellij.platform.jewel.intUi.standalone" to "jewel-int-ui-standalone",
    "intellij.platform.jewel.decoratedWindow" to "jewel-decorated-window",
  )

  private val ALL: PersistentMap<String, String> = CORE + STANDALONE

  internal val ALL_MODULES: Set<String> = ALL.keys

  init {
    check(ALL.values.toSet() == transitiveJewelDependencies.keys) { "One or more Jewel dependencies are mismatched" }
  }

  private val VERSION: String by lazy {
    val jewelProperties = COMMUNITY_ROOT.communityRoot.resolve("platform/jewel/gradle.properties")
    check(jewelProperties.exists()) { "$jewelProperties is missing" }
    DependenciesProperties(COMMUNITY_ROOT, jewelProperties).property("jewel.release.version")
  }

  fun isPublishedJewelModule(module: JpsModule): Boolean =
    module.name.startsWith("intellij.platform.jewel.") && module.name !in NOT_PUBLISHED

  /**
   * The icons modules required in Jewel Standalone and published to Maven Central transitively through
   * the published Jewel artifacts. Keep this allow-list aligned with [PLATFORM_DEPENDENCY_PREFIXES].
   */
  fun isPublishedPlatformDependency(module: JpsModule): Boolean =
    module.name in JEWEL_STANDALONE_REQUIRED_ICONS_MODULES

  fun patchCoordinates(module: JpsModule, coordinates: MavenCoordinates): MavenCoordinates {
    check(isPublishedJewelModule(module))
    val version = "$VERSION-${coordinates.version}"
    val patched = coordinates.copy(groupId = GROUP_ID, version = version)
    checkNotNull(SemVer.parseFromText(version)) {
      "$patched is expected to match the Semantic Versioning, see https://semver.org"
    }
    return patched
  }

  fun patchDependencies(module: JpsModule, dependencies: List<MavenArtifactDependency>): List<MavenArtifactDependency> = buildList {
    for (dependency in dependencies) {
      val coordinates = dependency.coordinates

      when (coordinates.groupId) {
        GROUP_ID -> {
          // Do not add transitive dependencies directly, let them be transitive
          if (module.isTransitiveJewelDependency(coordinates)) {
            continue
          }

          // Dependencies on other Jewel modules are always "compile", unless they are
          // Markdown extensions in the Markdown styling package (which are "runtime")
          val isMarkdownStylingModule = module.name == "intellij.platform.jewel.markdown.intUiStandaloneStyling"
          val isMarkdownExtensionDependency = coordinates.artifactId.startsWith("jewel-markdown-extensions-")
          val scope = if (isMarkdownStylingModule && isMarkdownExtensionDependency) {
            DependencyScope.RUNTIME
          }
          else {
            DependencyScope.COMPILE
          }

          add(dependency.withTransitiveDependencies(scope))
        }
        "org.jetbrains.compose.foundation" if module.name == "intellij.platform.jewel.foundation" -> {
          // Only add the Compose dependency to foundation, let other modules get it transitively
          add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
        }
        "org.commonmark" -> {
          // Add CommonMark dependencies as "compile" dependencies when present
          add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
        }
        "io.coil-kt.coil3" -> {
          // Add Coil 3 dependencies as "compile" dependencies when present
          add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
        }
        "org.jetbrains.compose.components" -> {
          add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
        }
        "net.java.dev.jna" -> {
          // Add it only to Jewel Standalone INT UI modules, as it's unnecessary for other modules
          if (module.name == "intellij.platform.jewel.intUi.standalone") {
            add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
          }
        }
        "com.jetbrains.intellij.platform" -> {
          // Publish the Icons API modules (icons-api / icons-api-rendering / icons-impl) as compile
          // dependencies, so consumers of the Jewel Standalone artifacts get IconManager on their
          // classpath and can both boot IntUiTheme and compile against the public Icon/iconKey APIs.
          if (coordinates.artifactId.startsWith("icons-")) {
            add(dependency.withTransitiveDependencies(DependencyScope.COMPILE))
          }
        }

        // else -> ignore the dependency, as it comes through transitively, usually from Compose.

        // Example of skipped dependencies:
        // * org.jetbrains:annotations
        // * org.jetbrains.skiko:*
        // * org.jetbrains.kotlin:kotlin-stdlib
        // * org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm — we want to use the "normal" one, not the IJP fork
      }
    }
  }

  private fun MavenArtifactDependency.withTransitiveDependencies(scope: DependencyScope) =
    copy(scope = scope, excludedDependencies = emptyList(), includeTransitiveDeps = true)

  /**
   * Patches the dependencies of the [isPublishedPlatformDependency] modules, which are consumed outside the IDE
   * once published to Maven Central.
   *
   * These modules depend on the IJP coroutines fork. That is fine in the IDE, where the fork is already on the
   * platform classpath, but it breaks standalone consumers (see [IJP_COROUTINES_FORK_GROUP]). They only use stock
   * coroutines API, so the dependency is repointed at the stock artifact: the artifactId is the same in both
   * groups, and dropping the [IJP_COROUTINES_FORK_VERSION_SUFFIX] suffix gives the stock version the fork is based
   * on.
   */
  fun patchPlatformDependencies(
    module: JpsModule,
    dependencies: List<MavenArtifactDependency>,
  ): List<MavenArtifactDependency> {
    check(isPublishedPlatformDependency(module))
    return dependencies.map { dependency ->
      val coordinates = dependency.coordinates
      val patched = if (coordinates.groupId == IJP_COROUTINES_FORK_GROUP) {
        check(coordinates.version.contains(IJP_COROUTINES_FORK_VERSION_SUFFIX)) {
          "Cannot derive the stock coroutines version from $coordinates: the version does not contain " +
          "'$IJP_COROUTINES_FORK_VERSION_SUFFIX'. The fork's versioning scheme has changed, so this mapping needs " +
          "updating; publishing it as-is would point consumers at a $STOCK_COROUTINES_GROUP version that " +
          "does not exist."
        }
        dependency.copy(
          coordinates = coordinates.copy(
            groupId = STOCK_COROUTINES_GROUP,
            version = coordinates.version.substringBefore(IJP_COROUTINES_FORK_VERSION_SUFFIX),
          )
        )
      }
      else {
        dependency
      }
      checkCoroutinesAreStock(module, patched.coordinates)
      patched
    }
  }

  // Fails the build if [coordinates] is a coroutines artifact from anywhere other than [STOCK_COROUTINES_GROUP].
  private fun checkCoroutinesAreStock(module: JpsModule, coordinates: MavenCoordinates) {
    if (!coordinates.artifactId.startsWith(COROUTINES_ARTIFACT_PREFIX)) return
    check(coordinates.groupId == STOCK_COROUTINES_GROUP) {
      "The POM for ${module.name} would declare $coordinates, but only $STOCK_COROUTINES_GROUP may provide " +
      "$COROUTINES_ARTIFACT_PREFIX artifacts. A fork published under any other groupId cannot be conflict-resolved " +
      "against the stock artifact, so consumers would end up with both jars on the classpath."
    }
  }

  private fun JpsModule.isTransitiveJewelDependency(coordinates: MavenCoordinates): Boolean {
    val moduleArtifactId = ALL[name] ?: error("Unknown Jewel module: $name")
    val artifactId = coordinates.artifactId
    val moduleJewelDeps = transitiveJewelDependencies[moduleArtifactId]
                          ?: error("Unknown Jewel module dependencies for $moduleArtifactId")
    return artifactId in moduleJewelDeps
  }

  fun addPomMetadata(module: JpsModule, model: Model) {
    check(isPublishedJewelModule(module))
    model.name = "Jewel"
    model.description = "A theme for Compose for Desktop that implements the IntelliJ Platform look and feel."
    model.addLicense(License().apply {
      name = "Apache License 2.0"
      url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    })
    model.addDeveloper(Developer().apply {
      id = "Google"
      name = "Google Team"
      organization = "Google"
      organizationUrl = "https://developer.android.com"
    })
  }

  /**
   * Supplies the POM metadata required by Maven Central for the [isPublishedPlatformDependency] modules.
   * The `name`, `url`, `scm`, `developers` and `organization` fields are already filled in by the POM
   * generator for these community modules; only `description` and `licenses` are missing.
   */
  fun addPlatformPomMetadata(module: JpsModule, model: Model) {
    check(isPublishedPlatformDependency(module))
    model.description = "IntelliJ Platform icons API."
    model.addLicense(License().apply {
      name = "Apache License 2.0"
      url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    })
  }

  private fun JpsModule.modulesTree(): Sequence<JpsModule> {
    return sequenceOf(this) + dependenciesList
      .dependencies
      .asSequence()
      .filterIsInstance<JpsModuleDependency>()
      .filter { isProductionDependency(it) }
      .mapNotNull { it.module }
      .flatMap { it.modulesTree() }
  }

  private fun isProductionDependency(dep: JpsDependencyElement): Boolean {
    val scope = JpsJavaExtensionService.getInstance().getDependencyExtension(dep)?.scope ?: return false
    return scope == JpsJavaDependencyScope.COMPILE || scope == JpsJavaDependencyScope.PROVIDED
  }

  fun validate(context: BuildContext, mavenArtifacts: Collection<GeneratedMavenArtifacts>) {
    ALL_MODULES.asSequence()
      .map { context.outputProvider.findRequiredModule(it) }
      .flatMap { it.modulesTree() }
      .distinct().forEach { module ->
        val artifact = mavenArtifacts.singleOrNull { (it) -> it.name == module.name }
        if (!module.isLibraryModule()) {
          checkNotNull(artifact) {
            "No maven artifact is created for the module ${module.name}:\n$mavenArtifacts"
          }
          check(artifact.coordinates.groupId == GROUP_ID || PLATFORM_DEPENDENCY_PREFIXES.any { "${artifact.coordinates}".startsWith(it) }) {
            "A ${module.name} module has unknown groupId=${artifact.coordinates.groupId}, " +
            "it is not allowed because Maven Central publication credentials are issued per namespace/groupId"
          }
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
    checkNoCoroutinesForkIsPublished(mavenArtifacts)
  }

  /**
   * Fails the build if the IJP coroutines fork leaks into a published POM (see [IJP_COROUTINES_FORK_GROUP]).
   * Consumers only hit that once they package an app, so it is easy to ship by accident.
   *
   * This checks the generated POMs rather than the patched dependency lists, so it also catches the fork arriving
   * transitively through a dependency republished as-is.
   */
  private fun checkNoCoroutinesForkIsPublished(mavenArtifacts: Collection<GeneratedMavenArtifacts>) {
    for ((module, coordinates, files) in mavenArtifacts) {
      val pomFileName = coordinates.getFileName(packaging = "pom")
      val pom = files.singleOrNull { it.name == pomFileName }
      checkNotNull(pom) { "No $pomFileName is generated for the module ${module.name}" }
      check(!pom.readText().contains(IJP_COROUTINES_FORK_GROUP)) {
        "The POM of $coordinates declares a dependency on $IJP_COROUTINES_FORK_GROUP. " +
        "The IJP coroutines fork must not be published to Maven Central: it shares the kotlinx.coroutines.* " +
        "packages with the stock artifact but uses a different groupId, so consumers get both jars on their " +
        "classpath and the fork can shadow stock API. See patchPlatformDependencies."
      }
    }
  }
}
