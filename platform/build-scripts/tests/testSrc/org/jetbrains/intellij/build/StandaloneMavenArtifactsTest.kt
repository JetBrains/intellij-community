// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.maven.MavenArtifactData
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.intellij.build.impl.maven.MavenPackaging
import org.jetbrains.intellij.build.impl.maven.renderPom
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile

/**
 * Checks the IntelliJ Standalone Analysis artifacts.
 *
 * @see [StandaloneMavenArtifacts] and `community/standalone-analysis/README.md`.
 */
class StandaloneMavenArtifactsTest {
  private val lifetime = BuildLifetime()

  @After
  fun closeBuildLifetime() {
    lifetime.close()
  }

  private val context by lazy {
    runBlocking {
      createBuildContext(
        projectHome = ULTIMATE_HOME,
        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
        setupTracer = false, lifetime = lifetime,
      )
    }
  }

  private val builder by lazy { MavenArtifactsBuilder(context) }

  private val layers: List<JpsModule> by lazy {
    StandaloneMavenArtifacts.LAYERS.map { context.outputProvider.findRequiredModule(it) }
  }

  /**
   * The artifact data of the layer modules and of every module in their runtime closure.
   */
  private val artifactData: Map<JpsModule, MavenArtifactData> by lazy {
    builder.generateMavenArtifactData(StandaloneMavenArtifacts.LAYERS, ignoreNonMavenizable = false)
  }

  @Test
  fun `layers are published by the community build as POM-only artifacts`() {
    val mavenArtifacts = context.productProperties.mavenArtifacts
    Assert.assertTrue(mavenArtifacts.additionalModules.containsAll(StandaloneMavenArtifacts.LAYERS))
    for (layer in layers) {
      Assert.assertTrue("The layer ${layer.name} must be a POM-only artifact", mavenArtifacts.isPomOnlyArtifact(layer))
      Assert.assertEquals(MavenPackaging.POM, artifactData.getValue(layer).packaging)
      val lowerLayerCoordinates = StandaloneMavenArtifacts.lowerLayers(layer).mapTo(HashSet()) { artifactData.getValue(it).coordinates }
      for (dependency in artifactData.getValue(layer).dependencies.filter { it.coordinates in lowerLayerCoordinates }) {
        Assert.assertEquals("The dependency on ${dependency.coordinates} must have the POM packaging", MavenPackaging.POM, dependency.packaging)
      }
    }
  }

  /**
   * Compares `pom.xml` of each layer with the `expected-pom.xml` file.
   * Run the test with `-Didea.tests.overwrite.data=true` to update the expected files.
   */
  @Test
  fun `pom of each layer matches the expected file`() {
    for (layer in layers) {
      val pom = renderPom(artifactData.getValue(layer).withKotlinVersionPlaceholder(), context).replace(context.buildNumber, VERSION_PLACEHOLDER)
      UsefulTestCase.assertSameLinesWithFile(moduleDir(layer).resolve(EXPECTED_POM_FILE_NAME).toString(), pom)
    }
  }

  /**
   * Compares the classpath of each layer with the `expected-classpath.txt` file: the modules and the libraries that a consumer gets
   * through the layer and not through a lower layer. A new dependency of a core module changes the file, a library update does not.
   * Run the test with `-Didea.tests.overwrite.data=true` to update the expected files.
   */
  @Test
  fun `classpath of each layer matches the expected file`() {
    val moduleCoordinates = artifactData.values.mapTo(HashSet()) { it.coordinates }
    for (layer in layers) {
      val librariesBelow = StandaloneMavenArtifacts.lowerLayers(layer).flatMapTo(HashSet()) { libraries(it, moduleCoordinates) }
      val classpath = buildString {
        appendLine("# modules")
        for (module in StandaloneMavenArtifacts.ownModules(layer).sortedBy { it.name }) {
          val coordinates = artifactData.getValue(module).coordinates
          appendLine("${module.name} ${coordinates.groupId}:${coordinates.artifactId}")
        }
        appendLine()
        appendLine("# libraries")
        for (library in (libraries(layer, moduleCoordinates) - librariesBelow).sorted()) {
          appendLine(library)
        }
      }
      UsefulTestCase.assertSameLinesWithFile(moduleDir(layer).resolve(EXPECTED_CLASSPATH_FILE_NAME).toString(), classpath)
    }
  }

  /**
   * Compares the module source files that more than one module in the closure of each layer has with the `expected-duplicate-sources.txt`
   * file. The Kotlin compiler and the Analysis API pack the sources of the closure into one JAR, which keeps only one copy of such a file.
   * A layer reports only the duplicates that it adds to its lower layers.
   * `community/standalone-analysis/classpath-test` checks the class jars. No test checks the library sources.
   * Run the test with `-Didea.tests.overwrite.data=true` to update the expected files.
   */
  @Test
  fun `duplicate module sources of each layer match the expected file`() {
    for (layer in layers) {
      val lowerOwners = StandaloneMavenArtifacts.lowerLayers(layer).map { moduleSourceOwners(it) }
      val duplicates = buildString {
        for ((path, modules) in moduleSourceOwners(layer).toSortedMap()) {
          if (modules.size > 1 && lowerOwners.none { it[path] == modules }) {
            appendLine(path)
            modules.forEach { appendLine("  $it") }
          }
        }
      }
      UsefulTestCase.assertSameLinesWithFile(moduleDir(layer).resolve(EXPECTED_DUPLICATE_SOURCES_FILE_NAME).toString(), duplicates)
    }
  }

  @Test
  fun `every module is compatible with Java 8`() {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val incompatible = (layers + layers.flatMap { StandaloneMavenArtifacts.ownModules(it) })
      .filter { javaExtensionService.getLanguageLevel(it).let { level -> level == null || level > LanguageLevel.JDK_1_8 } }
      .map { "${it.name}: ${javaExtensionService.getLanguageLevel(it)}" }
    Assert.assertTrue("Modules of the Standalone Analysis artifacts must have the Java 8 language level, but: $incompatible", incompatible.isEmpty())
  }

  @Test
  fun `smoke scenarios depend on the artifact modules only`() {
    val artifactModules = (layers + layers.flatMap { StandaloneMavenArtifacts.ownModules(it) }).mapTo(HashSet()) { it.name }
    for (smokeModuleName in listOf("intellij.standaloneAnalysis.platform.smoke", "intellij.standaloneAnalysis.java.smoke")) {
      val smokeModule = context.outputProvider.findRequiredModule(smokeModuleName)
      val outside = smokeModule.dependenciesList.dependencies
        .filterIsInstance<JpsModuleDependency>()
        .map { it.moduleReference.moduleName }
        .filter { it !in artifactModules }
        .sorted()
      Assert.assertTrue("The smoke scenario module $smokeModuleName depends on modules outside the Standalone Analysis artifacts: $outside", outside.isEmpty())
    }
  }

  /**
   * Replaces the version of each `org.jetbrains.kotlin` dependency with [KOTLIN_VERSION_PLACEHOLDER].
   */
  private fun MavenArtifactData.withKotlinVersionPlaceholder(): MavenArtifactData {
    return copy(dependencies = dependencies.map { dependency ->
      if (dependency.coordinates.groupId == KOTLIN_GROUP_ID) {
        dependency.copy(coordinates = dependency.coordinates.copy(version = KOTLIN_VERSION_PLACEHOLDER))
      }
      else {
        dependency
      }
    })
  }

  /**
   * The libraries that the POMs of [layer] and of its own modules declare, as `groupId:artifactId`.
   */
  private fun libraries(layer: JpsModule, moduleCoordinates: Set<MavenCoordinates>): Set<String> {
    return (listOf(layer) + StandaloneMavenArtifacts.ownModules(layer))
      .flatMap { artifactData.getValue(it).dependencies }
      .map { it.coordinates }
      .filter { it !in moduleCoordinates }
      .mapTo(LinkedHashSet()) { "${it.groupId}:${it.artifactId}" }
  }

  /**
   * Maps each `.java` and `.kt` file in the `-sources.jar` files of the closure of [layer] to the names of the modules with the file.
   * The path of a file is the path in the `-sources.jar`: the package prefix of the source root and the path in the root,
   * as `MavenArtifactsBuilder` packs it.
   */
  private fun moduleSourceOwners(layer: JpsModule): Map<String, Set<String>> {
    val owners = HashMap<String, MutableSet<String>>()
    val modules = (listOf(layer) + StandaloneMavenArtifacts.lowerLayers(layer))
      .flatMap { StandaloneMavenArtifacts.ownModules(it) }
      .distinct()

    for (module in modules.filter { context.productProperties.mavenArtifacts.publishSourcesFilter(it, context) }) {
      for (root in module.getSourceRoots(JavaSourceRootType.SOURCE).filter { it.path.exists() }) {
        val prefix = root.properties.packagePrefix.replace('.', '/')
        val files = Files.walk(root.path).use { stream ->
          stream.filter { it.isRegularFile() && (it.toString().endsWith(".java") || it.toString().endsWith(".kt")) }.toList()
        }
        for (file in files) {
          val relativePath = root.path.relativize(file).invariantSeparatorsPathString
          owners.getOrPut(if (prefix.isEmpty()) relativePath else "$prefix/$relativePath") { TreeSet() }.add(module.name)
        }
      }
    }
    return owners
  }

  private fun moduleDir(module: JpsModule): Path = Path.of(JpsPathUtil.urlToPath(module.contentRootsList.urls.single()))

  private companion object {
    const val EXPECTED_POM_FILE_NAME = "expected-pom.xml"
    const val EXPECTED_CLASSPATH_FILE_NAME = "expected-classpath.txt"
    const val EXPECTED_DUPLICATE_SOURCES_FILE_NAME = "expected-duplicate-sources.txt"
    const val VERSION_PLACEHOLDER = "BUILD_NUMBER"
    const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"
    const val KOTLIN_VERSION_PLACEHOLDER = "KOTLIN_VERSION"
  }
}
