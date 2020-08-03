// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext

import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

@CompileStatic
class SourcesStateProcessor {
  private static final Type SOURCES_STATE_TYPE = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType()

  private static final String SOURCES_STATE_FILE_NAME = 'target_sources_state.json'
  private static final String IDENTIFIER = '$BUILD_DIR$'

  private static final List<String> PRODUCTION_TYPES = ["java-production", "resources-production"]
  private static final List<String> TEST_TYPES = ["java-test", "resources-test"]
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"

  private final Gson gson = new Gson()
  private final CompilationContext context

  SourcesStateProcessor(CompilationContext context) {
    this.context = context
  }

  List<CompilationOutput> getAllCompilationOutputs(Map<String, Map<String, BuildTargetState>> sourceStateFile) {
    return getProductionCompilationOutputs(sourceStateFile) + getTestsCompilationOutputs(sourceStateFile)
  }

  Map<String, Map<String, BuildTargetState>> parseSourcesStateFile(String json = FileUtil.loadFile(sourceStateFile, CharsetToolkit.UTF8)) {
    return gson.fromJson(json, SOURCES_STATE_TYPE)
  }

  File getSourceStateFile() {
    return new File(context.compilationData.dataStorageRoot, SOURCES_STATE_FILE_NAME)
  }

  private List<CompilationOutput> getProductionCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return getCompilationOutputs(PRODUCTION, PRODUCTION_TYPES[0], PRODUCTION_TYPES[1], currentSourcesState)
  }

  private List<CompilationOutput> getTestsCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return getCompilationOutputs(TEST, TEST_TYPES[0], TEST_TYPES[1], currentSourcesState)
  }

  private List<CompilationOutput> getCompilationOutputs(String prefix, String firstUploadParam, String secondUploadParam,
                                                       Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    def root = new File(context.paths.buildOutputRoot, 'classes')

    def firstParamMap = currentSourcesState.get(firstUploadParam)
    def secondParamMap = currentSourcesState.get(secondUploadParam)

    def firstParamKeys = new HashSet<>(firstParamMap.keySet())
    def secondParamKeys = new HashSet<>(secondParamMap.keySet())
    def intersection = firstParamKeys.intersect(secondParamKeys)

    def compilationOutputs = []

    intersection.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      def hash = calculateStringHash(firstParamState.hash + secondParamState.hash)
      compilationOutputs << new CompilationOutput(buildTargetName, prefix, hash, outputPath)
    }

    firstParamKeys.removeAll(intersection)
    firstParamKeys.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      compilationOutputs << new CompilationOutput(buildTargetName, firstUploadParam, firstParamState.hash, outputPath)
    }

    secondParamKeys.removeAll(intersection)
    secondParamKeys.each { buildTargetName ->
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = secondParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      compilationOutputs << new CompilationOutput(buildTargetName, secondUploadParam, secondParamState.hash, outputPath)
    }

    return compilationOutputs
  }

  private static String calculateStringHash(String content) {
    def hasher = Hashing.murmur3_128().newHasher()
    return hasher.putString(content, StandardCharsets.UTF_8).hash().toString()
  }
}
