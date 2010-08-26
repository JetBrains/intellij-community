package com.intellij.codeInsight.completion;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.util.ArrayUtil;

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

    doTest "class Bar {{ abcm<caret> }}", """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

  @Override protected void tearDown() {
    CodeInsightSettings.instance.EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY
    super.tearDown()
  }

  public void testExcludeClassFromCompletion() throws Exception {
    myFixture.addClass("""package foo;
      public class Foo {
        public static int abcmethod() {}
      }
    """)
    myFixture.addClass("""package foo;
      public class Excl {
        public static int abcmethod2() {}
      }
    """)

    CodeInsightSettings.instance.EXCLUDED_PACKAGES = ["foo.Excl"] as String[]

    doTest "class Bar {{ abcm<caret> }}", """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

  public void testExcludeMethodFromCompletion() throws Exception {
    myFixture.addClass("""package foo;
      public class Foo {
        public static int abcmethod1() {}
        public static int abcmethodExcluded() {}
      }
    """)

    CodeInsightSettings.instance.EXCLUDED_PACKAGES = ["foo.Foo.abcmethodExcluded"] as String[]

    doTest "class Bar {{ abcm<caret> }}", """import static foo.Foo.abcmethod1;

class Bar {{ abcmethod1()<caret> }}"""
  }

  private void doTest(String input, String output) {
    myFixture.configureByText("a.java", input)

    assertOneElement myFixture.complete(CompletionType.CLASS_NAME)
    myFixture.type('\n')
    myFixture.checkResult output
  }

}
