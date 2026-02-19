// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jpsCache

import org.jetbrains.intellij.build.impl.compilation.CompilationOutput
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.ResourcesTargetType
import org.jetbrains.jps.cache.model.BuildTargetState
import java.nio.file.Path

private const val IDENTIFIER = "\$BUILD_DIR\$"

private enum class BothClassAndResourceBuildTargetType(@JvmField val typeId: String) {
  PRODUCTION("production"),
  TEST("test"),
  ;

  override fun toString() = typeId
}

internal fun getAllCompilationOutputs(sourceState: Map<String, Map<String, BuildTargetState>>, classOutDir: Path): List<CompilationOutput> {
  return getProductionCompilationOutputs(sourceState, classOutDir) + getTestsCompilationOutputs(sourceState, classOutDir)
}

private fun getProductionCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>, classOutDir: Path): List<CompilationOutput> {
  return getCompilationOutputs(
    // production modules with both classes and resources
    bothClassAndResourceBuildTargetType = BothClassAndResourceBuildTargetType.PRODUCTION,
    // production modules with classes only
    classBuildTargetType = JavaModuleBuildTargetType.PRODUCTION,
    // production modules with resources only
    resourcesBuildTargetType = ResourcesTargetType.PRODUCTION,
    currentSourceState = currentSourcesState,
    classOutDir = classOutDir,
  )
}

private fun getTestsCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>, classOutDir: Path): List<CompilationOutput> {
  return getCompilationOutputs(
    // test modules with both classes and resources
    bothClassAndResourceBuildTargetType = BothClassAndResourceBuildTargetType.TEST,
    // test modules with classes only
    classBuildTargetType = JavaModuleBuildTargetType.TEST,
    // test modules with resources only
    resourcesBuildTargetType = ResourcesTargetType.TEST,
    currentSourceState = currentSourcesState,
    classOutDir = classOutDir,
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
  bothClassAndResourceBuildTargetType: BothClassAndResourceBuildTargetType,
  classBuildTargetType: JavaModuleBuildTargetType,
  resourcesBuildTargetType: ResourcesTargetType,
  currentSourceState: Map<String, Map<String, BuildTargetState>>,
  classOutDir: Path,
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
        typeId = bothClassAndResourceBuildTargetType.typeId,
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
        typeId = classBuildTargetType.typeId,
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
        typeId = resourcesBuildTargetType.typeId,
        // `r` stands for `resources`
        hash = toHashPartOfUrl(buildTargetState.hash) + "-r",
        path = buildTargetState,
        root = root,
      )
    )
  }

  return compilationOutputs
}

private fun createCompilationOutputItem(
  typeId: String,
  buildTargetId: String,
  hash: String,
  path: BuildTargetState,
  root: String,
): CompilationOutput {
  return CompilationOutput(remotePath = "$typeId/$buildTargetId/$hash", path = Path.of(path.relativePath.replace(IDENTIFIER, root)))
}

private fun toHashPartOfUrl(classBuildTargetState: BuildTargetState, resourceBuildTargetState: BuildTargetState): String {
  // `cr` stands for `classes;resources`
  return "${toHashPartOfUrl(classBuildTargetState.hash)}-${toHashPartOfUrl(resourceBuildTargetState.hash)}-cr"
}

private fun toHashPartOfUrl(hash: Long): String {
  return java.lang.Long.toUnsignedString(hash, Character.MAX_RADIX)
}
