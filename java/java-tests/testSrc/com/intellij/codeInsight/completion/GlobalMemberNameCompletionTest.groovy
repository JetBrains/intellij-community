package com.intellij.codeInsight.completion
import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
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

    doTest "class Bar {{ abcm<caret> }}", true, """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

  public void testFieldName() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcfield = 2;
  static final int fieldThatsNotVisible = 3;
}
""")

    doTest "class Bar {{ abcf<caret> }}", true, """import static foo.Foo.abcfield;

class Bar {{ abcfield<caret> }}"""
  }

  public void testFieldNameQualified() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcfield = 2;
  static final int fieldThatsNotVisible = 3;
}
""")

    doTest "class Bar {{ abcf<caret> }}", false, """import foo.Foo;

class Bar {{ Foo.abcfield<caret> }}"""
  }

  public void testFieldNamePresentation() {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcfield = 2;
  static final int fieldThatsNotVisible = 3;
}
""")
    myFixture.configureByText "a.java", "class Bar {{ abcf<caret> }}"
    def element = complete()[0]
    def presentation = LookupElementPresentation.renderElement(element)
    assert 'Foo.abcfield' == presentation.itemText
    assert ' (foo)' == presentation.tailText
    assert 'int' == presentation.typeText
  }

  private LookupElement[] complete() {
    myFixture.complete(CompletionType.BASIC, 2)
  }

  public void testQualifiedMethodName() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcmethod() {}
}
""")

    doTest "class Bar {{ abcm<caret> }}", false, """import foo.Foo;

class Bar {{ Foo.abcmethod()<caret> }}"""
  }

  public void testIfThereAreAlreadyStaticImportsWithThatClass() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int anotherMethod(int a) {}
  public static int abcmethod() {}
  void methodThatsNotVisible() {}
}
""")

    doTest """import static foo.Foo.abcmethod;

class Bar {{ abcmethod(); anoMe<caret> }}""", false,
           """import static foo.Foo.abcmethod;
import static foo.Foo.anotherMethod;

class Bar {{ abcmethod(); anotherMethod(<caret>) }}"""
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

    JavaProjectCodeInsightSettings.setExcludedNames(project, testRootDisposable, "foo.Excl")

    doTest "class Bar {{ abcm<caret> }}", true, """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

  public void testExcludeMethodFromCompletion() throws Exception {
    myFixture.addClass("""package foo;
      public class Foo {
        public static int abcmethod1() {}
        public static int abcmethodExcluded() {}
      }
    """)

    JavaProjectCodeInsightSettings.setExcludedNames(project, testRootDisposable, "foo.Foo.abcmethodExcluded")

    doTest "class Bar {{ abcm<caret> }}", true, """import static foo.Foo.abcmethod1;

class Bar {{ abcmethod1()<caret> }}"""
  }

  public void testMergeOverloads() throws Exception {
    myFixture.addClass("""package foo;
      public class Foo {
        public static int abcmethod(int a) {}
        public static int abcmethod(boolean a) {}
        public static int abcmethod1(boolean a) {}
      }
    """)

    myFixture.configureByText("a.java", "class Bar {{ abcm<caret> }}")
    def element = complete()[0]

    def tail = LookupElementPresentation.renderElement(element).tailFragments
    assert tail[0].text == '(...)'
    assert !tail[0].grayed
    assert tail[1].text == ' (foo)'
    assert tail[1].grayed

    assertOrderedEquals myFixture.lookupElementStrings, "abcmethod", "abcmethod1"
  }

  public void testMethodFromTheSameClass() {
    myFixture.configureByText("a.java", """
class A {
  static void foo() {}

  static void goo() {
    f<caret>
  }
}
""")
    def element = complete()[0]
    def presentation = LookupElementPresentation.renderElement(element)
    assert 'foo' == presentation.itemText
    myFixture.type '\n'
    myFixture.checkResult '''
class A {
  static void foo() {}

  static void goo() {
    foo();<caret>
  }
}
'''
  }

  public void "test static import before an identifier"() {
    myFixture.addClass '''
package test.t1;

public enum DemoEnum
{
        XXONE,
        TWO
}'''
    doTest """
import test.t1.DemoEnum;

public class Demo {

        public static void doStuff(DemoEnum enumValue, String value) {}
        public static void main(String[] args)
        {
                String val = "anyValue";
                doStuff(XXON<caret>val);
        }
}
""", true, """
import test.t1.DemoEnum;

import static test.t1.DemoEnum.XXONE;

public class Demo {

        public static void doStuff(DemoEnum enumValue, String value) {}
        public static void main(String[] args)
        {
                String val = "anyValue";
                doStuff(XXONE<caret>val);
        }
}
"""
  }

  private void doTest(String input, boolean importStatic, String output) {
    myFixture.configureByText("a.java", input)

    def item = assertOneElement(complete())
    if (importStatic) {
      item.'as'(StaticallyImportable).shouldBeImported = true
    }
    myFixture.type('\n')
    myFixture.checkResult output
  }

  public void "test no results in incomplete static import"() {
    def text = """
package p;

import static Foo.ba<caret>

class Foo {
  static void bar() {}
}
"""
    def file = myFixture.addFileToProject("p/Foo.java", text)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()
    myFixture.checkResult text
  }

}
