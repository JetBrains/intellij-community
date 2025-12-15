// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.InvalidateCacheService
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.BDDAssertions.then
import org.junit.Test
import java.io.IOException
import kotlin.io.path.*
import kotlin.reflect.jvm.jvmName

class ExternalProjectsDataStorageTest : UsefulTestCase() {
  lateinit var myFixture: IdeaProjectTestFixture

  override fun setUp() {
    super.setUp()
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture
    myFixture.setUp()
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `test external project data is saved and loaded`() = runBlocking<Unit> {
    val dataStorage = ExternalProjectsDataStorage(myFixture.project)

    val testSystemId = ProjectSystemId("Test")
    val externalName = "external_name"
    val externalProjectInfo = createExternalProjectInfo(
      testSystemId,
      externalName,
      createTempDirectory(externalName).invariantSeparatorsPathString
    )

    dataStorage.update(externalProjectInfo)
    dataStorage.save()
    dataStorage.load()

    val list = dataStorage.list(testSystemId)
    then(list).hasSize(1)
    then(list.iterator().next().externalProjectStructure?.data?.externalName).isEqualTo(externalName)
  }

  @Test
  fun `test external project data updated before storage initialization is not lost`() = runBlocking<Unit> {
    val dataStorage = ExternalProjectsDataStorage(myFixture.project)

    val testSystemId = ProjectSystemId("Test")
    val externalName1 = "external_name1"
    dataStorage.update(
      createExternalProjectInfo(
        testSystemId,
        externalName1,
        createTempDirectory(externalName1).invariantSeparatorsPathString
      )
    )
    dataStorage.load()

    val externalName2 = "external_name2"
    dataStorage.update(
      createExternalProjectInfo(
        testSystemId,
        externalName2,
        createTempDirectory(externalName2).invariantSeparatorsPathString
      )
    )

    val list = dataStorage.list(testSystemId)
    then(list).hasSize(2)
    val thenList = then(list)
    thenList.anyMatch { it.externalProjectStructure?.data?.externalName == externalName1 }
    thenList.anyMatch { it.externalProjectStructure?.data?.externalName == externalName2 }
  }

  fun `test external project data is marked as broken after invalidating`() = runBlocking {
    val brokenMarkerPath = PathManager.getSystemDir().resolve("external_build_system").resolve(".broken")

    val brokenContent: String? = try {
      brokenMarkerPath.readText()
    }
    catch (_: IOException) {
      null
    }

    try {
      brokenMarkerPath.deleteIfExists()
      brokenMarkerPath.parent.deleteIfExists()
      InvalidateCacheService.invalidateCaches { true }
      assertTrue("Broken marker should exists after invalidating caches", brokenMarkerPath.exists())
    }
    finally {
      if (brokenContent != null) {
        brokenMarkerPath.createParentDirectories()
        brokenMarkerPath.writeText(brokenContent)
      }
    }
  }

  private fun createExternalProjectInfo(
    testId: ProjectSystemId,
    externalName: String,
    externalProjectPath: String,
  ): InternalExternalProjectInfo {
    val projectData = ProjectData(testId, externalName, externalProjectPath, externalProjectPath)
    val node = DataNode(Key(ProjectData::class.jvmName, 0), projectData, null)
    return InternalExternalProjectInfo(testId, externalProjectPath, node)
  }
}