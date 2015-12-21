/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author cdr
 */
public class HighlightUsagesHandlerTest extends LightCodeInsightFixtureTestCase {
  final String basePath = JavaTestUtil.relativeJavaTestDataPath

  public void testHighlightImport() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'import', 'List', 'List', 'List', 'List', 'List'
    checkUnselect()
  }

  public void testHighlightStaticImport() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'import', 'abs', 'abs', 'pow'
    checkUnselect()
  }

  public void testSimpleThrows() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', 'Exception'
    checkUnselect()
  }

  public void testThrowsExpression() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', '(Exception)detail'
    checkUnselect()
  }

  public void testThrowsReference() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', 'detail'
    checkUnselect()
  }

  public void testUnselectUsage() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'foo', 'foo', 'foo'
    checkUnselect()
  }

  public void testHighlightOverridden() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'extends', 'foo'
    checkUnselect()
  }

  public void testHighlightOverriddenImplements() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'implements', 'foo'
    checkUnselect()
  }

  public void testHighlightOverriddenNothing() {
    configureFile()
    ctrlShiftF7()
    assertRangeText()
    checkUnselect()
  }

  public void testHighlightOverriddenMultiple() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'implements', 'foo', 'other'
    checkUnselect()
  }

  public void testIDEADEV28822() {
    myFixture.configureByText 'Foo.java', '''
      public class Foo {
        public String foo(String s) {
          while (s.length() > 0) {
            if (s.length() < 0) {
              s = "";
              continue;
            }
            else {
            }
          }
          <caret>return s;
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'return s;'
  }

  public void testReturnsInTryFinally() {
    // See IDEADEV-14028
    myFixture.configureByText 'Foo.java', '''
      public class Foo {
        int foo(boolean b) {
          try {
            if (b) return 1;
          }
          finally {
            if (b) return 2;
          }
          <caret>return 3;
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'return 1;', 'return 2;', 'return 3;'
  }

  public void testReturnsInLambda() {
    // See IDEADEV-14028
    myFixture.configureByText 'Foo.java', '''
      public class Foo {
        {
          Runnable r = () -> {
                 if (true) return;
                 <caret>return;
          }
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'return;', 'return;'
  }

  public void testSuppressedWarningsHighlights() {
    myFixture.configureByText 'Foo.java', '''
      public class Foo {
        @SuppressWarnings({"Sil<caret>lyAssignment"})
        void foo() {
            int i = 0;
            i = i;
        }
      }'''.stripIndent()
    myFixture.enableInspections(new SillyAssignmentInspection())
    ctrlShiftF7()
    assertRangeText 'i = i'
  }

  public void "test statically imported overloads from usage"() {
    myFixture.addClass '''
      class Foo {
        static void foo(int a) {}
        static void foo(int a, int b) {}
      }'''.stripIndent()
    myFixture.configureByText 'Bar.java', '''
      import static Foo.foo;

      class Bar {
        {
          <caret>foo(1);
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'foo', 'foo'
  }

  public void "test statically imported overloads from import"() {
    myFixture.addClass '''
      class Foo {
        static void foo(int a) {}
        static void foo(int a, int b) {}
      }'''.stripIndent()
    myFixture.configureByText 'Bar.java', '''
      import static Foo.<caret>foo;

      class Bar {
        {
          foo(1);
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'foo', 'foo', 'foo' //import highlighted twice: for each overloaded usage target
  }

  public void "test identifier highlighter for static imports"() {
    myFixture.addClass '''
      class Foo {
        static void foo(int a) {}
        static void foo(int a, int b) {}
      }'''.stripIndent()
    myFixture.configureByText 'Bar.java', '''
      import static Foo.fo<caret>o;

      class Bar {
        {
          foo(1);
        }
      }'''.stripIndent()
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled {
      def infos = myFixture.doHighlighting()
      //import highlighted twice: for each overloaded usage target
      assert infos.findAll {
        it.severity == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY &&
        myFixture.file.text.substring(it.startOffset, it.endOffset) == 'foo'
      }.size() == 3
    }
  }

  private void configureFile() {
    def testName = getTestName(false)
    def file = myFixture.copyFileToProject "/codeInsight/highlightUsagesHandler/${testName}.java", "${testName}.java"
    myFixture.configureFromExistingVirtualFile(file)
  }

  private void ctrlShiftF7() {
    HighlightUsagesHandler.invoke myFixture.project, myFixture.editor, myFixture.file
  }

  private void assertRangeText(String... texts) {
    def highlighters = myFixture.editor.markupModel.allHighlighters
    def actual = highlighters.collect { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    assertSameElements actual, texts
  }

  private void checkUnselect() {
    ctrlShiftF7()
    assertRangeText()
  }
}