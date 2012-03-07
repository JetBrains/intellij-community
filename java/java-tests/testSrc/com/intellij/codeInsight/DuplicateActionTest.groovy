package com.intellij.codeInsight;


import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NonNls

public class DuplicateActionTest extends LightCodeInsightFixtureTestCase {
  public void testOneLine() {
    doTest '''xxx<caret>
''', "txt", '''xxx
xxx<caret>
'''
  }

  public void testEmpty() {
    doTest '<caret>', "txt", '\n<caret>'
  }

  private void doTest(String before, @NonNls String ext, String after) {
    myFixture.configureByText("a." + ext, before);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE)
    myFixture.checkResult(after);
  }

  public void testSelectName() {
    doTest '''
class C {
  void foo() {}<caret>
}
''', 'java', '''
class C {
  void foo() {}
  void <caret>foo() {}
}
'''
  }
}
