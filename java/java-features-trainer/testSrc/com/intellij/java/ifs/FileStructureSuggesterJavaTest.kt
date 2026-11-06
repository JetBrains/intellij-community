// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.focusEditor
import training.featuresSuggester.FeatureSuggesterTestUtils.logicalPositionToOffset
import training.featuresSuggester.FeatureSuggesterTestUtils.performFindInFileAction
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.featureSuggesterFixture

@TestApplication
class FileStructureSuggesterJavaTest {
  private val suggester by featureSuggesterFixture(JavaSuggestersTestUtils.testDataPath, "JavaCodeExample.java", "File structure")

  @Test
  fun `testFind field and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(1, 0)
      performFindInFileAction("field", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind method and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(4, 0)
      performFindInFileAction("mai", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind class and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(14, 0)
      performFindInFileAction("staticCl", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind function parameter and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(4, 0)
      performFindInFileAction("args", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind local variable declaration and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(4, 0)
      performFindInFileAction("abc", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind variable usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(7, 0)
      performFindInFileAction("fiel", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind method usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(5, 0)
      performFindInFileAction("main", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind type usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(5, 0)
      performFindInFileAction("Static", fromOffset)
      focusEditor()
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
