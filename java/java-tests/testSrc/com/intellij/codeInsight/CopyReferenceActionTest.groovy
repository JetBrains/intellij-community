package com.intellij.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NonNls

public class CopyReferenceActionTest extends LightCodeInsightFixtureTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/copyReference";
  protected int oldSetting;

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    oldSetting = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    settings.ADD_IMPORTS_ON_PASTE = oldSetting;
    super.tearDown();
  }

  public void testConstructor() throws Exception { doTest(); }
  public void testDefaultConstructor() throws Exception { doTest(); }
  public void testIdentifierSeparator() throws Exception { doTest(); }
  public void testMethodFromAnonymousClass() throws Exception { doTest(); }

  public void testAddImport() {
    myFixture.addClass("package foo; public class Foo {}")
    myFixture.configureByText "a.java", "import foo.F<caret>oo;"
    performCopy();
    myFixture.configureByText "b.java", "class Goo { <caret> }"
    performPaste();
    myFixture.checkResult """import foo.Foo;

class Goo { Foo }"""

  }

  public void "test paste correct signature to javadoc"() {
    myFixture.configureByText "a.java", """
class Foo {
  void foo(int a) {}
  void foo<caret>(byte a) {}
}
"""
    performCopy()
    myFixture.configureByText "b.java", "/** <caret> */"
    performPaste()
    myFixture.checkResult "/** Foo#foo(byte)<caret> */"
  }

  public void "test paste correct generic signature to javadoc"() {
    myFixture.configureByText "a.java", """
class Foo {
  void foo<caret>(java.util.List<String[]> a) {}
}
"""
    performCopy()
    myFixture.configureByText "b.java", "/** <caret> */"
    performPaste()
    myFixture.checkResult "/** Foo#foo(java.util.List)<caret> */"
  }

  public void "test paste overloaded signature to a comment"() {
    myFixture.configureByText "a.java", """
class Foo {
  void foo<caret>(int a) {} //
  void foo(int a, int b) {}
}
"""
    performCopy()
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf('//') + 2)
    performPaste()
    myFixture.checkResult """
class Foo {
  void foo(int a) {} //Foo.foo(int)<caret>
  void foo(int a, int b) {}
}
"""
  }
  
  public void testFqnInImport() {
    myFixture.addClass("package foo; public class Foo {}")
    myFixture.configureByText "a.java", "import foo.F<caret>oo;"
    performCopy();
    myFixture.configureByText "b.java", "import <caret>"
    performPaste();
    myFixture.checkResult """import foo.Foo<caret>"""

  }

  public void testCopyFile() throws Exception {
    PsiFile psiFile = myFixture.addFileToProject("x/x.txt", "");
    assertTrue(CopyReferenceAction.doCopy(psiFile, getProject()));

    String name = getTestName(false);
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  public void testCopyLineNumber() {
    myFixture.configureByText 'a.java', '''
<caret>class Foo {
}'''
    performCopy()
    myFixture.configureByText 'a.txt', ''
    performPaste()
    myFixture.checkResult "a.java:2"
  }

  public void testMethodOverloadCopy() {
    myFixture.configureByText 'a.java', '''
class Koo {
  public void foo(int a) { }
  public void foo(boolean a) { }
  
  {
    fo<caret>o(true);
  }
}'''
    performCopy()
    myFixture.configureByText 'b.java', '''
/**
 * <caret>
 */
class Koo2 { }
'''
    performPaste()
    myFixture.checkResult '''
/**
 * Koo#foo(boolean)<caret>
 */
class Koo2 { }
'''
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    performCopy();
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  private void performCopy() {
    myFixture.testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY_REFERENCE));
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }
}
