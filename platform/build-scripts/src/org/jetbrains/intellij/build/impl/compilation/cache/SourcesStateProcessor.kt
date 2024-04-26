// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.util.lang.Xxh3
import org.jetbrains.intellij.build.impl.compilation.CompilationOutput
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.ResourcesTargetType
import org.jetbrains.jps.cache.model.BuildTargetState
import java.nio.file.Files
import java.nio.file.Path

class SourcesStateProcessor(dataStorageRoot: Path, private val classesOutputDirectory: Path) {
  companion object {
    private val SOURCES_STATE_TYPE = object : TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.type

    private const val SOURCES_STATE_FILE_NAME = "target_sources_state.json"
    private const val IDENTIFIER = "\$BUILD_DIR\$"

    private const val PRODUCTION = "production"
    private const val TEST = "test"
  }

  private val gson: Gson = Gson()
  val sourceStateFile: Path = dataStorageRoot.resolve(SOURCES_STATE_FILE_NAME)

  internal fun getAllCompilationOutputs(sourceStateFile: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getProductionCompilationOutputs(sourceStateFile) + getTestsCompilationOutputs(sourceStateFile)
  }

  internal fun parseSourcesStateFile(json: String = Files.readString(sourceStateFile)): Map<String, Map<String, BuildTargetState>> {
    return gson.fromJson(json, SOURCES_STATE_TYPE)
  }

  private fun getProductionCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getCompilationOutputs(
      // production modules with both classes and resources
      PRODUCTION,
      // production modules with classes only
      JavaModuleBuildTargetType.PRODUCTION,
      // production modules with resources only
      ResourcesTargetType.PRODUCTION,
      currentSourcesState
    )
  }

  private fun getTestsCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getCompilationOutputs(
      // test modules with both classes and resources
      TEST,
      // test modules with classes only
      JavaModuleBuildTargetType.TEST,
      // test modules with resources only
      ResourcesTargetType.TEST,
      currentSourcesState
    )
  }

  private fun getCompilationOutputs(bothClassesAndResourcesBuildTargetType: String,
                                    classesBuildTargetType: JavaModuleBuildTargetType,
                                    resourcesBuildTargetType: ResourcesTargetType,
                                    currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    val root = classesOutputDirectory.toFile()

    val classesBuildTargetMap = currentSourcesState.getValue(classesBuildTargetType.typeId)
    val resourcesBuildTargetMap = currentSourcesState.getValue(resourcesBuildTargetType.typeId)

    val classesBuildTargetIds = HashSet<String>(classesBuildTargetMap.keys)
    val resourcesBuildTargetIds = HashSet<String>(resourcesBuildTargetMap.keys)
    val bothClassesAndResourcesBuildTargetIds = classesBuildTargetIds.intersect(resourcesBuildTargetIds)

    val compilationOutputs = ArrayList<CompilationOutput>(classesBuildTargetIds.size + resourcesBuildTargetIds.size - bothClassesAndResourcesBuildTargetIds.size)

    bothClassesAndResourcesBuildTargetIds.forEach { buildTargetId ->
      val classesBuildTargetState = classesBuildTargetMap.getValue(buildTargetId)
      val resourcesBuildTargetState = resourcesBuildTargetMap.getValue(buildTargetId)
      val outputPath = classesBuildTargetState.relativePath.replace(IDENTIFIER, root.absolutePath)

      val hash = calculateStringHash(classesBuildTargetState.hash + resourcesBuildTargetState.hash)
      compilationOutputs.add(CompilationOutput(buildTargetId, bothClassesAndResourcesBuildTargetType, hash, outputPath))
    }

    classesBuildTargetIds.removeAll(bothClassesAndResourcesBuildTargetIds)
    classesBuildTargetIds.forEach { buildTargetId ->
      val buildTargetState = classesBuildTargetMap.getValue(buildTargetId)
      val outputPath = buildTargetState.relativePath.replace(IDENTIFIER, root.absolutePath)

      compilationOutputs.add(CompilationOutput(buildTargetId, classesBuildTargetType.typeId, buildTargetState.hash, outputPath))
    }

    resourcesBuildTargetIds.removeAll(bothClassesAndResourcesBuildTargetIds)
    resourcesBuildTargetIds.forEach { buildTargetId ->
      val buildTargetState = resourcesBuildTargetMap.getValue(buildTargetId)
      val outputPath = buildTargetState.relativePath.replace(IDENTIFIER, root.absolutePath)

      compilationOutputs.add(CompilationOutput(buildTargetId, resourcesBuildTargetType.typeId, buildTargetState.hash, outputPath))
    }

    return compilationOutputs
  }

}

private fun calculateStringHash(content: String): String {
  return Xxh3.hash(content).toString()
}
