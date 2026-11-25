// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixFixture
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixTestFiles
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInInitMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.function.Supplier

@TestApplication
class DisableHighlightingActionTest {
  companion object {
    private val TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/disableHighlighting"
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())

    @JvmStatic
    fun files(): List<String> = lightQuickFixTestFiles(TEST_DATA_PATH)
  }

  private val quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH, Supplier { listOf(SillyAssignmentInspection()) })
  private var myKey: HighlightDisplayKey? = null

  @BeforeEach
  fun setUpKey() {
    myKey = HighlightDisplayKey.find(SillyAssignmentInspection().shortName)
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  fun parameterized(fileName: String) {
    val quickFix = quickFixFixture.get()
    val hint = quickFix.configure(fileName)
    //ensure action which access inspection profile can do that
    runInInitMode {
      quickFix.checkAction(fileName, hint)
    }
    assertEquals(HighlightDisplayLevel.DO_NOT_SHOW,
                 InspectionProjectProfileManager.getInstance(quickFix.fixture.project).currentProfile.getErrorLevel(myKey!!, null))
  }
}
