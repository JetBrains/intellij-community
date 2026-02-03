// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.java.codeInsight.completion.CompletionHintsTest
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class ExcludeListMethodIntentionTest : LightJavaCodeInsightFixtureTestCase() {

  private val default = ParameterNameHintsSettings()

  override fun tearDown() {
    try {
      ParameterNameHintsSettings.getInstance().loadState(default.state)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
  
  fun `test add to blacklist by alt enter`() {
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
  
  fun `test add elements which has inlays`() {
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
  
  private fun assertHintExistsAndDisappearsAfterIntention() {
    myFixture.doHighlighting()

    val caretOffset = editor.caretModel.offset
    val before = editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
    assertThat(before).isNotEmpty

    val intention = myFixture.getAvailableIntention("Do not show hints for current method")
    myFixture.launchAction(intention!!)
    myFixture.doHighlighting()
    CompletionHintsTest.waitTillAnimationCompletes(editor)

    val after = editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
    assertThat(after).isEmpty()
  }
  
}