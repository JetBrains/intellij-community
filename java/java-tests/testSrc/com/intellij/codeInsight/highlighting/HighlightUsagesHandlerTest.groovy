/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NonNls
/**
 * @author cdr
 */
public class HighlightUsagesHandlerTest extends LightCodeInsightFixtureTestCase {
  private void ctrlShiftF7() {
    HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  public void testHighlightImport() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "List", "List", "List", "List", "List");
    checkUnselect();
  }

  public void testHighlightStaticImport() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "abs", "abs", "pow");
    checkUnselect();
  }

  public void testSimpleThrows() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "Exception");
    checkUnselect();
  }
  public void testThrowsExpression() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "(Exception)detail");
    checkUnselect();
  }
  public void testThrowsReference() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "detail");
    checkUnselect();
  }

  private void checkUnselect() {
    ctrlShiftF7();
    assertRangeText();
  }

  public void testUnselectUsage() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo");
    checkUnselect();
  }

  public void testHighlightOverridden() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("extends", "foo");
    checkUnselect();
  }
  public void testHighlightOverriddenImplements() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo");
    checkUnselect();
  }
  public void testHighlightOverriddenNothing() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText();
    checkUnselect();
  }
  public void testHighlightOverriddenMultiple() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo", "other");
    checkUnselect();
  }

  public void testIDEADEV28822() throws Exception {
    myFixture.configureByText("Foo.java",
                          """public class Foo {public String foo(String s) {
    while (s.length() > 0) {
      if (s.length() < 0) {
        s = "";
        continue;
      }
      else {
      }
    }
    re<caret>turn s;
  }
}""");
    ctrlShiftF7();
    assertRangeText("return s;");
  }
            
  public void testReturnsInTryFinally() throws Exception {
    // See IDEADEV-14028
    myFixture.configureByText("Foo.java",
                          """public class Foo {
  int foo(boolean b) {
    try {
      if (b) return 1;
    }
    finally {
      if (b) return 2;
    }
    r<caret>eturn 3;
  }
}""");

    ctrlShiftF7();
    assertRangeText("return 1;", "return 2;", "return 3;");
  }

  public void testReturnsInLambda() throws Exception {
    // See IDEADEV-14028
    myFixture.configureByText("Foo.java",
                          """public class Foo {
  {
    Runnable r = () -> {
           if (true) return;
           retur<caret>n;
    }
  }
}""");

    ctrlShiftF7();
    assertRangeText("return;", "return;");
  }

  public void testSuppressedWarningsHighlights() throws Exception {
    myFixture.configureByText("Foo.java", """public class Foo {
        @SuppressWarnings({"Sil<caret>lyAssignment"})
        void foo() {
            int i = 0;
            i = i;
        }
    }""");
    myFixture.enableInspections(new SillyAssignmentInspection())
    ctrlShiftF7();
    assertRangeText("i = i");
  }

  private void assertRangeText(@NonNls String... texts) {
    def highlighters = myFixture.editor.getMarkupModel().getAllHighlighters()
    def actual = highlighters.collect { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    assertSameElements(actual, texts)
  }
  
  private void configureFile() throws Exception {
    def file = myFixture.copyFileToProject("/codeInsight/highlightUsagesHandler/" + getTestName(false) + ".java", getTestName(false) + ".java")
    myFixture.configureFromExistingVirtualFile(file)
  }

  public void "test statically imported overloads from usage"() {
    myFixture.addClass("""
class Foo { 
  static void foo(int a) {}  
  static void foo(int a, int b) {}  
}""")
    myFixture.configureByText 'Bar.java', '''
import static Foo.foo;

class Bar {
  {
    <caret>foo(1);
  }
}
'''
    ctrlShiftF7()
    assertRangeText 'foo', 'foo'
  }

  public void "test statically imported overloads from import"() {
    myFixture.addClass("""
class Foo { 
  static void foo(int a) {}  
  static void foo(int a, int b) {}  
}""")
    myFixture.configureByText 'Bar.java', '''
import static Foo.fo<caret>o;

class Bar {
  {
    foo(1);
  }
}
'''
    ctrlShiftF7()
    assertRangeText 'foo', 'foo', 'foo' //import highlighted twice: for each overloaded usage target
  }

  public void "test identifier highlighter for static imports"() {
    myFixture.addClass("""
class Foo { 
  static void foo(int a) {}  
  static void foo(int a, int b) {}  
}""")
    myFixture.configureByText 'Bar.java', '''
import static Foo.fo<caret>o;

class Bar {
  {
    foo(1);
  }
}
'''
    IdentifierHighlighterPassFactory.ourTestingIdentifierHighlighting = true
    try {
      def infos = myFixture.doHighlighting()
      //import highlighted twice: for each overloaded usage target
      assert infos.findAll { it.severity == HighlightSeverity.INFORMATION && myFixture.file.text.substring(it.startOffset, it.endOffset) == 'foo' }.size() == 3
    }
    finally {
      IdentifierHighlighterPassFactory.ourTestingIdentifierHighlighting = false
    }
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.relativeJavaTestDataPath
  }
}
