// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.checkClassFiles
import org.jetbrains.intellij.build.impl.libraries.isLibraryModule
import org.jetbrains.intellij.build.impl.maven.GeneratedMavenArtifacts
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import kotlin.io.path.name

/**
 * The IntelliJ Standalone Analysis artifacts: the platform core and the Java PSI for a tool that runs outside the IDE,
 * such as the Kotlin compiler and the Kotlin Analysis API.
 *
 * Each layer is a POM-only artifact of an aggregator module. The POM lists the direct dependencies of the module,
 * and Maven resolves the rest through the regular module artifacts. See `community/standalone-analysis/README.md`.
 */
object StandaloneMavenArtifacts {
  val LAYERS: PersistentList<String> = persistentListOf(
    "intellij.standaloneAnalysis.platform",
    "intellij.standaloneAnalysis.platform.impl",
    "intellij.standaloneAnalysis.java",
    "intellij.standaloneAnalysis.java.impl",
  )

  /**
   * The highest class file version allowed in the artifacts. The Kotlin compiler runs on Java 8, see OSIP-935.
   */
  const val CLASS_FILE_VERSION: String = "1.8"

  fun isLayer(module: JpsModule): Boolean = LAYERS.contains(module.name)

  /**
   * The layers below [layer]: every layer on its runtime classpath.
   */
  fun lowerLayers(layer: JpsModule): List<JpsModule> {
    return runtimeClosure(layer).filter { it != layer && isLayer(it) }
  }

  /**
   * The modules a consumer gets through [layer] and not through a lower layer: the runtime closure of [layer]
   * without the closures of the lower layers, the layers themselves, and the library modules.
   */
  fun ownModules(layer: JpsModule): List<JpsModule> {
    val below = lowerLayers(layer).flatMapTo(HashSet()) { runtimeClosure(it) }
    return runtimeClosure(layer).filter { it !in below && !isLayer(it) && !it.isLibraryModule() }
  }

  fun validate(mavenArtifacts: Collection<GeneratedMavenArtifacts>) {
    val layers = mavenArtifacts.filter { isLayer(it.module) }
    if (layers.isEmpty()) {
      return
    }

    val missingLayers = LAYERS.filter { layer -> layers.none { it.module.name == layer } }
    check(missingLayers.isEmpty()) {
      "No Maven artifact is generated for the Standalone Analysis modules $missingLayers, generated: ${layers.map { it.coordinates }}"
    }

    val artifactsByModule = mavenArtifacts.associateBy { it.module.name }
    for ((layer, coordinates, files) in layers) {
      val pom = coordinates.getFileName(packaging = "pom")
      check(files.map { it.name } == listOf(pom)) {
        "The layer $coordinates must be a POM-only artifact, but these files are generated: ${files.map { it.name }}"
      }
      for (module in ownModules(layer)) {
        val artifact = checkNotNull(artifactsByModule.get(module.name)) {
          "No Maven artifact is generated for the module ${module.name} of the layer $coordinates"
        }
        val jar = artifact.files.single { it.name == artifact.coordinates.getFileName(packaging = "jar") }
        checkClassFiles(
          versionCheckConfig = mapOf("" to CLASS_FILE_VERSION),
          forbiddenSubPaths = emptyList(),
          forbiddenSubPathExceptions = emptyList(),
          root = jar,
        )
      }
    }
  }
}

private fun runtimeClosure(module: JpsModule): List<JpsModule> {
  return JpsJavaExtensionService.dependencies(module)
    .recursively().withoutSdk().includedIn(JpsJavaClasspathKind.runtime(false))
    .modules.toList()
}