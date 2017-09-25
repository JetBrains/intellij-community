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
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull

class CopyReferenceActionTest extends LightCodeInsightFixtureTestCase {
  private int oldSetting

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/copyReference"
  }

  @Override
  protected void setUp() {
    super.setUp()
    CodeInsightSettings settings = CodeInsightSettings.getInstance()
    oldSetting = settings.ADD_IMPORTS_ON_PASTE
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance()
    settings.ADD_IMPORTS_ON_PASTE = oldSetting
    super.tearDown()
  }

  void testConstructor() { doTest() }
  void testDefaultConstructor() { doTest() }
  void testIdentifierSeparator() { doTest() }
  void testMethodFromAnonymousClass() { doTest() }

  void testSameClassNames() {
    myFixture.addClass("package p; public class Foo {}")
    myFixture.configureByText("Foo.java", "package p1; public class Fo<caret>o {}")
    performCopy()
    myFixture.configureByText("a.java", "import p.Foo; class Bar { <caret>}")
    performPaste()
    myFixture.checkResult """import p.Foo; class Bar { p1.Foo}"""
  }

  void testAddImport() {
    myFixture.addClass("package foo; public class Foo {}")
    myFixture.configureByText "a.java", "import foo.F<caret>oo;"
    performCopy()
    myFixture.configureByText "b.java", "class Goo { <caret> }"
    performPaste()
    myFixture.checkResult """import foo.Foo;

class Goo { Foo }"""
  }

  void "test paste correct signature to javadoc"() {
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

  void "test paste correct generic signature to javadoc"() {
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

  void "test paste overloaded signature to a comment"() {
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

  void testFqnInImport() {
    myFixture.addClass("package foo; public class Foo {}")
    myFixture.configureByText "a.java", "import foo.F<caret>oo;"
    performCopy()
    myFixture.configureByText "b.java", "import <caret>"
    performPaste()
    myFixture.checkResult """import foo.Foo<caret>"""
  }

  void testCopyFile() {
    PsiFile psiFile = myFixture.addFileToProject("x/x.txt", "")
    assertTrue(CopyReferenceAction.doCopy(psiFile, getProject()))

    String name = getTestName(false)
    myFixture.configureByFile(name + "_dst.java")
    performPaste()
    myFixture.checkResultByFile(name + "_after.java")
  }

  void testCopyLineNumber() {
    myFixture.configureByText 'a.java', '''
<caret>class Foo {
}'''
    performCopy()
    myFixture.configureByText 'a.txt', ''
    performPaste()
    myFixture.checkResult "a.java:2"
  }

  void testMethodOverloadCopy() {
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

  void testModuleNameCopy() {
    myFixture.configureByText 'module-info.java', 'module M16<caret> { }'
    performCopy()
    assert CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor) == 'M16'
  }

  void testModuleReferenceCopy() {
    myFixture.configureByText 'module-info.java', 'module M16 { requires M16<caret>; }'
    performCopy()
    assert CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor) == 'M16'
  }

  private void doTest() {
    String name = getTestName(false)
    myFixture.configureByFile(name + ".java")
    performCopy()
    myFixture.configureByFile(name + "_dst.java")
    performPaste()
    myFixture.checkResultByFile(name + "_after.java")
  }

  private void performCopy() {
    myFixture.testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY_REFERENCE))
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
  }
}