// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.common.hash.Hashing
import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic

import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

@CompileStatic
class SourcesStateProcessor {
  static final Type SOURCES_STATE_TYPE = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType()
  static final String IDENTIFIER = '$PROJECT_DIR$'

  private static final List<String> PRODUCTION_TYPES = ["java-production", "resources-production"]
  private static final List<String> TEST_TYPES = ["java-test", "resources-test"]
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"

  private SourcesStateProcessor() {}

  static List<CompilationOutput> getAllCompilationOutputs(String replaceToken, File root, Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return getProductionCompilationOutputs(replaceToken, root, currentSourcesState) + getTestsCompilationOutputs(replaceToken, root, currentSourcesState)
  }

  private static List<CompilationOutput> getProductionCompilationOutputs(String replaceToken, File root, Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return getCompilationOutputs(replaceToken, PRODUCTION, PRODUCTION_TYPES[0], PRODUCTION_TYPES[1], root, currentSourcesState)
  }

  private static List<CompilationOutput> getTestsCompilationOutputs(String replaceToken, File root, Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return getCompilationOutputs(replaceToken, TEST, TEST_TYPES[0], TEST_TYPES[1], root, currentSourcesState)
  }

  private static List<CompilationOutput> getCompilationOutputs(String replaceToken, String prefix, String firstUploadParam, String secondUploadParam, File root,
                                                       Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    def firstParamMap = currentSourcesState.get(firstUploadParam)
    def secondParamMap = currentSourcesState.get(secondUploadParam)

    def firstParamKeys = new HashSet<>(firstParamMap.keySet())
    def secondParamKeys = new HashSet<>(secondParamMap.keySet())
    def intersection = firstParamKeys.intersect(secondParamKeys)

    def compilationOutputs = []

    intersection.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(replaceToken, root.getAbsolutePath())

      def hash = calculateStringHash(firstParamState.hash + secondParamState.hash)
      compilationOutputs << new CompilationOutput(buildTargetName, prefix, hash, outputPath)
    }

    firstParamKeys.removeAll(intersection)
    firstParamKeys.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(replaceToken, root.getAbsolutePath())

      compilationOutputs << new CompilationOutput(buildTargetName, firstUploadParam, firstParamState.hash, outputPath)
    }

    secondParamKeys.removeAll(intersection)
    secondParamKeys.each { buildTargetName ->
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = secondParamState.relativePath.replace(replaceToken, root.getAbsolutePath())

      compilationOutputs << new CompilationOutput(buildTargetName, secondUploadParam, secondParamState.hash, outputPath)
    }

    return compilationOutputs
  }

  private static String calculateStringHash(String content) {
    def hasher = Hashing.murmur3_128().newHasher()
    return hasher.putString(content, StandardCharsets.UTF_8).hash().toString()
  }
}
