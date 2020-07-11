// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.highlighting

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.MyTestInjector
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class HighlightUsagesHandlerTest extends LightJavaCodeInsightFixtureTestCase {
  final String basePath = JavaTestUtil.relativeJavaTestDataPath

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14
  }

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
    IdeaTestUtil.withLevel module, LanguageLevel.JDK_14, {
      configureFile()
      ctrlShiftF7()
      assertRangeText 'switch', 'yield', 'yield'
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
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), getTestRootDisposable()) {
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

  void testMethodParameterEndOfIdentifier() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), getTestRootDisposable()) {
      configureFile()
      def infos = myFixture.doHighlighting()
      Segment[] segments = infos.findAll {
        it.severity == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY
      }
      assertSegments(segments, ["28:33 param", "60:65 param", "68:73 param"] as java.lang.String[])
    }
  }

  void testRecordComponents() {
    myFixture.configureByText 'A.java', '''
      record A(String s) {
        void test() {
          <caret>s();
          s();
          String a = s;
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangesAndTexts "17:18 s", "42:43 s", "51:52 s", "71:72 s"
  }

  void testCompactConstructorParameters() {
    myFixture.configureByText 'A.java', '''
      record A(String s) {
        A {
          <caret>s;
        }
      }'''.stripIndent()
    ctrlShiftF7()
    assertRangesAndTexts "17:18 s", "32:33 s"
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myFixture.setReadEditorMarkupModel(true)
  }

  /**
   * @param expected sorted by startOffset
   */
  private void assertRangesAndTexts(String... expected) {
    def highlighters = myFixture.editor.markupModel.allHighlighters
    assertSegments(highlighters, expected)
  }

  private void assertSegments(Segment[] highlighters, String... expected) {
    def actual = highlighters
      .sort { it.startOffset }
      .collect { "$it.startOffset:$it.endOffset ${myFixture.file.text.substring(it.startOffset, it.endOffset)}".toString() }
    assertSameElements(actual, expected)
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

  void testCaretOnExceptionInMethodThrowsDeclarationMustHighlightPlacesThrowingThisException() {
    String s = '''
      import java.io.*;
      class A {
        public static void deserialize(File file) throws <caret>IOException, java.lang.RuntimeException, ClassNotFoundException {
          boolean length = file.createNewFile();
          if (length == false) throw new RuntimeException();
          file.getCanonicalPath();
          if (length == true) throw new ClassNotFoundException();
        }
      }'''
    myFixture.configureByText 'A.java', s.stripIndent()

    HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(myFixture.editor, myFixture.file)
    assertNotNull(handler)
    List<PsiElement> targets = handler.targets
    assertEquals(1, targets.size())

    handler.computeUsages(targets)
    List<TextRange> readUsages = handler.readUsages
    List<String> expected = Arrays.asList('IOException', 'file.createNewFile', 'file.getCanonicalPath')
    assertEquals(expected.size(), readUsages.size())

    List<String> textUsages = readUsages.collect { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    assertSameElements(expected, textUsages)
  }
}