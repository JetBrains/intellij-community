// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.j2me

import com.intellij.JavaTestUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.j2me.SingleCharacterStartsWithInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

/**
 * @author Bas Leijdekkers
 */
@TestApplication
class SingleCharacterStartsWithLengthTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_4)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testUseLengthMethod(): Unit = timeoutRunBlocking { quickfixTest(myFixture) }
}

@TestApplication
class SingleCharacterStartsWithIsEmptyTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testUseIsEmptyMethod(): Unit = timeoutRunBlocking { quickfixTest(myFixture) }

  @Test
  fun testParentPolyadic(): Unit = timeoutRunBlocking { quickfixTest(myFixture) }
}

private suspend fun quickfixTest(myFixture: LightJavaCodeInsightTestFixture) {
  myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igfixes/j2me/single_character_starts_with"
  myFixture.enableInspections(SingleCharacterStartsWithInspection())
  myFixture.configureByFile(myFixture.getTestName(false) + ".java")
  myFixture.testHighlighting()
  withContext(Dispatchers.EDT) {
    myFixture.launchAction(myFixture.findSingleIntention(InspectionGadgetsBundle.message("single.character.startswith.quickfix")))
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResultByFile(myFixture.getTestName(false) + ".after.java")
  }
}
