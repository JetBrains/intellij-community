// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import com.intellij.testFramework.junit5.javaCodeInsightFixture
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path


@TestApplication
@RunInEdt(allMethods = false)
@TestDataPath($$"$PROJECT_ROOT/community/java/java-tests/testData/ig/com/siyeh/igtest/migration/try_finally_can_be_try_with_resources")
class TryFinallyCanBeTryWithResourcesInspectionTest {
  companion object {
    @BeforeAll
    @JvmStatic
    @RunMethodInEdt
    fun beforeAll() {
        val jdk = IdeaTestUtil.getMockJdk9()
        WriteAction.runAndWait<Exception> {
          ProjectJdkTable.getInstance().addJdk(jdk, disposable)
          ProjectRootManager.getInstance(project.get()).setProjectSdk(jdk)
          ModuleRootModificationUtil.setModuleSdk(module, jdk)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project.get())
    }

    private val disposable by disposableFixture()
    private val tempDir: TestFixture<Path> = tempPathFixture()
    private val project: TestFixture<Project> = projectFixture(tempDir, openAfterCreation = true)
    private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)
  }

  private val testName by testNameFixture(false)
  private val fixture by javaCodeInsightFixture(project, tempDir)

  @BeforeEach
  fun setUp() {
    fixture.enableInspections(TryFinallyCanBeTryWithResourcesInspection())
  }

  @Test
  fun tryFinallyCanBeTryWithResources() {
    fixture.testHighlighting("$testName.java")
  }
}