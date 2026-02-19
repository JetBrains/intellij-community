// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.jps.cache.loader

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.cache.client.JpsServerClient
import org.jetbrains.jps.cache.model.BuildTargetState
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path

private const val PRODUCTION = "production"
private const val TEST = "test"
private fun getTestDataFile(fileName: String): Path {
  return PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/cacheLoader").toPath().resolve(fileName)
}

class JpsCompilationOutputLoaderTest : BasePlatformTestCase() {
  private var compilationOutputLoader: JpsCompilationOutputLoader? = null

  public override fun setUp() {
    super.setUp()
    compilationOutputLoader = JpsCompilationOutputLoader(JpsServerClient.getServerClient(""), "/intellij/out/classes")
  }

  fun testCurrentModelStateNull() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(null, loadModelFromFile("caseOne.json"), false)
    UsefulTestCase.assertSize(4, affectedModules)
    // 836 production
    UsefulTestCase.assertSize(2, affectedModules.filter { it.type.contains(PRODUCTION) })
    // 407 test
    UsefulTestCase.assertSize(2, affectedModules.filter { it.type.contains(TEST) })
  }

  fun testChangedStatsCollectorModule() {
    val affectedModules =
      compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseOne.json"), loadModelFromFile("caseTwo.json"), false)
    assertThat(affectedModules).hasSize(1)
    val affectedModule = affectedModules[0]
    assertEquals("java-production", affectedModule!!.type)
    assertEquals("intellij.statsCollector", affectedModule.name)
  }

  fun testNewType() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseTwo.json"),
                                                                       loadModelFromFile("caseThree.json"), false)
    assertThat(affectedModules).hasSize(1)
    val affectedModule = affectedModules[0]
    assertEquals("artifacts", affectedModule!!.type)
    assertEquals("intellij.cidr.externalSystem", affectedModule.name)
  }

  fun testChangedProductionModule() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseTwo.json"),
                                                                       loadModelFromFile("caseFour.json"), false)
    assertThat(affectedModules).hasSize(1)
    val affectedModule = affectedModules[0]
    assertEquals(PRODUCTION, affectedModule!!.type)
    assertEquals("intellij.cidr.externalSystem", affectedModule.name)
  }

  fun testNewBuildModule() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseFour.json"),
                                                                       loadModelFromFile("caseFive.json"), false)
    assertThat(affectedModules).hasSize(1)
    val affectedModule = affectedModules[0]
    assertEquals("resources-production", affectedModule!!.type)
    assertEquals("intellij.sh.plugin", affectedModule.name)
  }

  fun testTargetFolderNotExist() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseFour.json"),
                                                                       loadModelFromFile("caseFive.json"), true)
    assertThat(affectedModules).hasSize(4)
    val types = affectedModules.map { it.type }
    val names = affectedModules.map { it.name }
    UsefulTestCase.assertSameElements(types, "java-test", "production", "resources-test", "resources-production")
    UsefulTestCase.assertSameElements(names, "intellij.cidr.externalSystem", "intellij.platform.ssh.integrationTests", "intellij.sh.plugin")
  }

  fun testChangedTest() {
    val affectedModules = compilationOutputLoader!!.getAffectedModules(loadModelFromFile("caseFive.json"),
                                                                       loadModelFromFile("caseSix.json"), false)
    assertThat(affectedModules).hasSize(1)
    val affectedModule = affectedModules[0]
    assertEquals(TEST, affectedModule!!.type)
    assertEquals("intellij.cidr.externalSystem", affectedModule.name)
  }

  fun testRemoveBuildType() {
    compilationOutputLoader!!.getAffectedModules(loadModelFromFile("removeOne.json"), loadModelFromFile("caseOne.json"), false)
    val oldModulesPaths = compilationOutputLoader!!.oldModulesPaths
    UsefulTestCase.assertSize(1, oldModulesPaths)
    assertEquals("intellij.cidr", oldModulesPaths[0]!!.name)
  }

  fun testRemoveModuleNotExistingInOtherBuildTypes() {
    compilationOutputLoader!!.getAffectedModules(loadModelFromFile("removeTwo.json"), loadModelFromFile("removeOne.json"), false)
    val oldModulesPaths = compilationOutputLoader!!.oldModulesPaths
    UsefulTestCase.assertSize(1, oldModulesPaths)
    assertEquals("intellij.platform.ssh.integrationTests", oldModulesPaths[0]!!.name)
  }

  fun testRemoveModuleExistingInOtherBuildTypes() {
    compilationOutputLoader!!.getAffectedModules(loadModelFromFile("removeThree.json"), loadModelFromFile("removeTwo.json"), false)
    val oldModulesPaths = compilationOutputLoader!!.oldModulesPaths
    UsefulTestCase.assertSize(0, oldModulesPaths)
  }
}

private fun loadModelFromFile(fileName: String): Map<String, Map<String, BuildTargetState>> {
  val inJson = Files.readString(getTestDataFile(fileName))
  val map = BuildTargetSourcesState.readJson(JsonReader(inJson.reader()))

  val stringWriter = StringWriter()
  JsonWriter(stringWriter).use { BuildTargetSourcesState.writeJson(it, map) }
  assertThat(BuildTargetSourcesState.readJson(JsonReader(stringWriter.toString().reader())))
    .isEqualTo(map)
  return map
}