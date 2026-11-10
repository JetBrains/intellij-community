// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.java.codeInsight.AbstractParameterInfoTestCase
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ExcludeListMethodIntentionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val default = ParameterNameHintsSettings()

  @AfterEach
  fun tearDown() {
    ParameterNameHintsSettings.getInstance().loadState(default.state)
  }
  
  @Test
  fun `test add to blacklist by alt enter`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("a.java", """
class Test {
  void test() {
    check(<caret>100);
  }
  void check(int isShow) {}
}
""")
    assertHintExistsAndDisappearsAfterIntention()
  }
  
  @Test
  fun `test add elements which has inlays`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("a.java", """
class ParamHintsTest {

    public static void main(String[] args) {
        Mvl(
                <caret>Math.abs(1) * 100, 32, 32
        );
    }

    public static double Mvl(double first, double x, double c) {
        return Double.NaN;
    }
}
""")
    assertHintExistsAndDisappearsAfterIntention()
  }
  
  private suspend fun assertHintExistsAndDisappearsAfterIntention() {
    myFixture.doHighlighting()

    withContext(Dispatchers.EDT) {
      val caretOffset = myFixture.editor.caretModel.offset
      val before = myFixture.editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
      assertThat(before).isNotEmpty

      val intention = myFixture.getAvailableIntention("Do not show hints for current method")
      myFixture.launchAction(intention!!)
      myFixture.doHighlighting()
      AbstractParameterInfoTestCase.waitTillAnimationCompletes(myFixture.editor)

      val after = myFixture.editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
      assertThat(after).isEmpty()
    }
  }
  
}
