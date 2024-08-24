// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.gson.stream.JsonReader
import org.jetbrains.intellij.build.impl.compilation.CompilationOutput
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.ResourcesTargetType
import org.jetbrains.jps.cache.model.BuildTargetState
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import java.nio.file.Files
import java.nio.file.Path

private const val SOURCES_STATE_FILE_NAME = "target_sources_state.json"
private const val IDENTIFIER = "\$BUILD_DIR\$"

private const val PRODUCTION = "production"
private const val TEST = "test"

internal class SourcesStateProcessor(dataStorageRoot: Path, private val classOutDir: Path) {
  @JvmField
  val sourceStateFile: Path = dataStorageRoot.resolve(SOURCES_STATE_FILE_NAME)

  internal fun getAllCompilationOutputs(sourceStateFile: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getProductionCompilationOutputs(sourceStateFile) + getTestsCompilationOutputs(sourceStateFile)
  }

  internal fun parseSourcesStateFile(json: String): Map<String, Map<String, BuildTargetState>> {
    return BuildTargetSourcesState.readJson(JsonReader(json.reader()))
  }

  internal fun parseSourcesStateFile(): Map<String, Map<String, BuildTargetState>> {
    return Files.newBufferedReader(sourceStateFile).use {
      BuildTargetSourcesState.readJson(JsonReader(it))
    }
  }

  private fun getProductionCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getCompilationOutputs(
      // production modules with both classes and resources
      bothClassesAndResourcesBuildTargetType = PRODUCTION,
      // production modules with classes only
      classBuildTargetType = JavaModuleBuildTargetType.PRODUCTION,
      // production modules with resources only
      resourcesBuildTargetType = ResourcesTargetType.PRODUCTION,
      currentSourceState = currentSourcesState,
    )
  }

  private fun getTestsCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getCompilationOutputs(
      // test modules with both classes and resources
      bothClassesAndResourcesBuildTargetType = TEST,
      // test modules with classes only
      classBuildTargetType = JavaModuleBuildTargetType.TEST,
      // test modules with resources only
      resourcesBuildTargetType = ResourcesTargetType.TEST,
      currentSourceState = currentSourcesState
    )
  }

  /**
   * We have class and resource build targets.
   * We produce a list of compilation output items.
   * Since both targets output to the same directory, we create one item for both class and resource targets for the same module.
   *
   * Naming convention:
   *
   * - `buildTargetHash-c` or `buildTargetHash-r` if the module has only one build target output (either class or resource).
   * - `classBuildTargetHash-resourceBuildTargetHash-cr` if the module has both class and resource build target outputs.
   *
   * Suffixes are used to avoid collisions.
   * Since the hash is only unique for a particular build target type (to some extent, depending on the hash function).
   */
  private fun getCompilationOutputs(
    bothClassesAndResourcesBuildTargetType: String,
    classBuildTargetType: JavaModuleBuildTargetType,
    resourcesBuildTargetType: ResourcesTargetType,
    currentSourceState: Map<String, Map<String, BuildTargetState>>,
  ): List<CompilationOutput> {
    val root = classOutDir.toAbsolutePath().toString()

    val classBuildTargetMap = currentSourceState.getValue(classBuildTargetType.typeId)
    val resourceBuildTargetMap = currentSourceState.getValue(resourcesBuildTargetType.typeId)

    val classBuildTargetIds = HashSet<String>(classBuildTargetMap.keys)
    val resourcesBuildTargetIds = HashSet<String>(resourceBuildTargetMap.keys)
    val bothClassAndResourceBuildTargetIds = classBuildTargetIds.intersect(resourcesBuildTargetIds)

    val compilationOutputs = ArrayList<CompilationOutput>((classBuildTargetIds.size + resourcesBuildTargetIds.size) - bothClassAndResourceBuildTargetIds.size)

    for (buildTargetId in bothClassAndResourceBuildTargetIds) {
      val classBuildTargetState = classBuildTargetMap.getValue(buildTargetId)
      compilationOutputs.add(
        createCompilationOutputItem(
          buildTargetId = buildTargetId,
          type = bothClassesAndResourcesBuildTargetType,
          hash = toHashPartOfUrl(classBuildTargetState, resourceBuildTargetMap.getValue(buildTargetId)),
          path = classBuildTargetState,
          root = root,
        )
      )
    }

    classBuildTargetIds.removeAll(bothClassAndResourceBuildTargetIds)
    for (buildTargetId in classBuildTargetIds) {
      val buildTargetState = classBuildTargetMap.getValue(buildTargetId)
      compilationOutputs.add(
        createCompilationOutputItem(
          type = classBuildTargetType.typeId,
          buildTargetId = buildTargetId,
          // `c` stands for `classes`
          hash = toHashPartOfUrl(buildTargetState.hash) + "-c",
          path = buildTargetState,
          root = root,
        )
      )
    }

    resourcesBuildTargetIds.removeAll(bothClassAndResourceBuildTargetIds)
    for (buildTargetId in resourcesBuildTargetIds) {
      val buildTargetState = resourceBuildTargetMap.getValue(buildTargetId)
      compilationOutputs.add(
        createCompilationOutputItem(
          buildTargetId = buildTargetId,
          type = resourcesBuildTargetType.typeId,
          // `r` stands for `resources`
          hash = toHashPartOfUrl(buildTargetState.hash) + "-r",
          path = buildTargetState,
          root = root,
        )
      )
    }

    return compilationOutputs
  }
}

private fun createCompilationOutputItem(
  type: String,
  buildTargetId: String,
  hash: String,
  path: BuildTargetState,
  root: String,
): CompilationOutput {
  return CompilationOutput(remotePath = "$type/$buildTargetId/$hash", path = Path.of(path.relativePath.replace(IDENTIFIER, root)))
}

private fun toHashPartOfUrl(classBuildTargetState: BuildTargetState, resourceBuildTargetState: BuildTargetState): String {
  // `cr` stands for `classes;resources`
  return "${toHashPartOfUrl(classBuildTargetState.hash)}-${toHashPartOfUrl(resourceBuildTargetState.hash)}-cr"
}

private fun toHashPartOfUrl(hash: Long): String {
  return java.lang.Long.toUnsignedString(hash, Character.MAX_RADIX)
}
