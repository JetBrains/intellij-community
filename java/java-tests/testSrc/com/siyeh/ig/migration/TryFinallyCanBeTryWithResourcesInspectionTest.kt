// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import com.intellij.testFramework.setUpJdk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


@TestApplication
@RunInEdt(allMethods = false)
@TestDataPath($$"$PROJECT_ROOT/community/java/java-tests/testData/ig/com/siyeh/igtest/migration/try_finally_can_be_try_with_resources")
class TryFinallyCanBeTryWithResourcesInspectionTest {
  companion object {
    @BeforeAll
    @JvmStatic
    @RunMethodInEdt
    fun beforeAll() {
      setUpJdk(LanguageLevel.JDK_1_9, project.get(), module, disposable)
    }

    private val disposable by disposableFixture()
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)
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