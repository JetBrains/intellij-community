// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.highlighting

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.impl.source.tree.injected.MyTestInjector
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author cdr
 */
class HighlightUsagesHandlerTest extends LightCodeInsightFixtureTestCase {
  final String basePath = JavaTestUtil.relativeJavaTestDataPath

  void testHighlightImport() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'import', 'List', 'List', 'List', 'List', 'List'
    checkUnselect()
  }

  void testHighlightStaticImport() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'import', 'abs', 'abs', 'pow'
    checkUnselect()
  }

  void testSimpleThrows() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', 'Exception'
    checkUnselect()
  }

  void testThrowsExpression() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', '(Exception)detail'
    checkUnselect()
  }

  void testThrowsReference() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'throws', 'detail'
    checkUnselect()
  }

  void testUnselectUsage() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'foo', 'foo', 'foo'
    checkUnselect()
  }

  void testHighlightOverridden() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'extends', 'foo'
    checkUnselect()
  }

  void testHighlightOverriddenImplements() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'implements', 'foo'
    checkUnselect()
  }

  void testHighlightOverriddenNothing() {
    configureFile()
    ctrlShiftF7()
    assertRangeText()
    checkUnselect()
  }

  void testHighlightOverriddenMultiple() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'implements', 'foo', 'other'
    checkUnselect()
  }

  void testBreakInSwitch() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'switch', 'break'
    checkUnselect()
  }

  void testBreakInSwitchExpr() {
    IdeaTestUtil.withLevel myModule, LanguageLevel.JDK_12_PREVIEW, {
      configureFile()
      ctrlShiftF7()
      assertRangeText 'switch', 'break', 'break'
      checkUnselect()
    }
  }

  void testBreakInDoWhile() {
    configureFile()
    ctrlShiftF7()
    assertRangeText 'break', 'continue', 'while'
    checkUnselect()
  }

  void testIDEADEV28822() {
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

  void testReturnsInTryFinally() {
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

  void testReturnsInLambda() {
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

  void testSuppressedWarningsHighlights() {
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
    assertRangeText 'i'
  }

  void testSuppressedWarningsInInjectionHighlights() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager())
    testInjector.injectAll(myFixture.getTestRootDisposable())
    myFixture.configureByText 'Foo.java', '''
      public class Foo {
        public static void a(boolean b, String c) {
           @SuppressWarnings({"SillyAssignment"})
           String java = "class A {{int i = 0; i = i;}}";
        }
      }'''.stripIndent()
    myFixture.enableInspections(new SillyAssignmentInspection())
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("illyAssignment"))
    ctrlShiftF7()
    assertRangeText '"class A {{int i = 0; i = i;}}"'
  }

  void "test statically imported overloads from usage"() {
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

  void "test statically imported overloads from import"() {
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

  void "test identifier highlighter for static imports"() {
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

  void testExceptionsInTryWithResources() {
    myFixture.configureByText 'A.java', '''
      import java.io.*;
      class A {
        void test() throws IOException {
          try (InputStream in = new FileInputStream("file.name")) { }
          <caret>catch (FileNotFoundException e) { throw new FileNotFoundException("no highlighting here"); }
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'FileInputStream', 'catch'
  }

  void testExceptionsResourceCloser() {
    myFixture.configureByText 'A.java', '''
      import java.io.*;
      class A {
        void test() {
          try (InputStream in = new FileInputStream("file.name")) { }
          <caret>catch (IOException e) { }
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangeText 'in', 'FileInputStream', 'FileInputStream', 'catch'
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