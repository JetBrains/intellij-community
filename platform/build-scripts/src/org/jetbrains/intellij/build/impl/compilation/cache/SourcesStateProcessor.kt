// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.jps.cache.model.BuildTargetState
import org.jetbrains.intellij.build.impl.compilation.CompilationOutput
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class SourcesStateProcessor(dataStorageRoot: Path, private val classesOutputDirectory: Path) {
  companion object {
    private val SOURCES_STATE_TYPE = object : TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.type

    private const val SOURCES_STATE_FILE_NAME = "target_sources_state.json"
    private const val IDENTIFIER = "\$BUILD_DIR\$"

    private val PRODUCTION_TYPES = listOf("java-production", "resources-production")
    private val TEST_TYPES = listOf("java-test", "resources-test")
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
    return getCompilationOutputs(PRODUCTION, PRODUCTION_TYPES[0], PRODUCTION_TYPES[1], currentSourcesState)
  }

  private fun getTestsCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    return getCompilationOutputs(TEST, TEST_TYPES[0], TEST_TYPES[1], currentSourcesState)
  }

  private fun getCompilationOutputs(prefix: String,
                                    firstUploadParam: String,
                                    secondUploadParam: String,
                                    currentSourcesState: Map<String, Map<String, BuildTargetState>>): List<CompilationOutput> {
    val root = classesOutputDirectory.toFile()

    val firstParamMap = currentSourcesState[firstUploadParam]!!
    val secondParamMap = currentSourcesState[secondUploadParam]!!

    val firstParamKeys = HashSet<String>(firstParamMap.keys)
    val secondParamKeys = HashSet<String>(secondParamMap.keys)
    val intersection = firstParamKeys.intersect(secondParamKeys)

    val compilationOutputs = ArrayList<CompilationOutput>(firstParamKeys.size + secondParamKeys.size - intersection.size)

    intersection.forEach { buildTargetName ->
      val firstParamState = firstParamMap[buildTargetName]!!
      val secondParamState = secondParamMap[buildTargetName]!!
      val outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.absolutePath)

      val hash = calculateStringHash(firstParamState.hash + secondParamState.hash)
      compilationOutputs.add(CompilationOutput(buildTargetName, prefix, hash, outputPath))
    }

    firstParamKeys.removeAll(intersection)
    firstParamKeys.forEach { buildTargetName ->
      val firstParamState = firstParamMap[buildTargetName]!!
      val outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.absolutePath)

      compilationOutputs.add(CompilationOutput(buildTargetName, firstUploadParam, firstParamState.hash, outputPath))
    }

    secondParamKeys.removeAll(intersection)
    secondParamKeys.forEach { buildTargetName ->
      val secondParamState = secondParamMap[buildTargetName]!!
      val outputPath = secondParamState.relativePath.replace(IDENTIFIER, root.absolutePath)

      compilationOutputs.add(CompilationOutput(buildTargetName, secondUploadParam, secondParamState.hash, outputPath))
    }

    return compilationOutputs
  }

}

private fun calculateStringHash(content: String): String {
  val hasher = Hashing.murmur3_128().newHasher()
  return hasher.putString(content, StandardCharsets.UTF_8).hash().toString()
}
