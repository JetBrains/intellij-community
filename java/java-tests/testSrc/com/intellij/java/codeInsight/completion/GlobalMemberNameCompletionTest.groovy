/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StaticallyImportable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.ClassConditionKey
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class GlobalMemberNameCompletionTest extends LightCodeInsightFixtureTestCase {

  void testMethodName() throws Exception {
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

  void testFieldName() throws Exception {
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

  void testFieldNameQualified() throws Exception {
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

  void testFieldNamePresentation() {
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

  void testQualifiedMethodName() throws Exception {
    myFixture.addClass("""
package foo;

public class Foo {
  public static int abcmethod() {}
}
""")

    doTest "class Bar {{ abcm<caret> }}", false, """import foo.Foo;

class Bar {{ Foo.abcmethod()<caret> }}"""
  }

  void testIfThereAreAlreadyStaticImportsWithThatClass() throws Exception {
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


  void testExcludeClassFromCompletion() throws Exception {
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

    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "*Excl")

    doTest "class Bar {{ abcm<caret> }}", true, """import static foo.Foo.abcmethod;

class Bar {{ abcmethod()<caret> }}"""
  }

  void testExcludeMethodFromCompletion() throws Exception {
    myFixture.addClass("""package foo;
      public class Foo {
        public static int abcmethod1() {}
        public static int abcmethodExcluded() {}
      }
    """)

    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo.Foo.abcmethodExcluded")

    doTest "class Bar {{ abcm<caret> }}", true, """import static foo.Foo.abcmethod1;

class Bar {{ abcmethod1()<caret> }}"""
  }

  void testMergeOverloads() throws Exception {
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

  void testMethodFromTheSameClass() {
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

  void "test static import before an identifier"() {
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
      ClassConditionKey<StaticallyImportable> key = ClassConditionKey.create(StaticallyImportable.class)
      item.'as'(key).shouldBeImported = true
    }
    myFixture.type('\n')
    myFixture.checkResult output
  }

  void "test no results in incomplete static import"() {
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

  void "test no global reformatting"() {
    CodeStyleSettingsManager.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_BINARY_OPERATION = true

    myFixture.addClass("package foo; public interface Foo { int XCONST = 42; }")
    def file = myFixture.addFileToProject "a.java", '''
class Zoo {
    void zoo(int i) {
        if (i == XCONS<caret> ||
            CONTAINERS.contains(myElementType)) {
        } 
    }
}
'''
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type('\n')
    myFixture.checkResult '''\
import foo.Foo;

class Zoo {
    void zoo(int i) {
        if (i == Foo.XCONST<caret> ||
            CONTAINERS.contains(myElementType)) {
        } 
    }
}
'''
  }
}
