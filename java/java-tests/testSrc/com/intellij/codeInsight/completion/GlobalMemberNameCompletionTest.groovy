package com.intellij.codeInsight.completion;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class GlobalMemberNameCompletionTest extends LightCodeInsightFixtureTestCase {

  public void testMethodName() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcmethod() {}
  static void methodThatsNotVisible() {}
}
""")

    myFixture.configureByText("a.java", "class Bar {{ abcm<caret> }}")

    myFixture.complete(CompletionType.CLASS_NAME)
    myFixture.type('\n')
    myFixture.checkResult """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

}
