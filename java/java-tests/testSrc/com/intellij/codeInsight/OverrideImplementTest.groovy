/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author ven
 */
public class OverrideImplementTest extends LightCodeInsightFixtureTestCase {
  private static final String BASE_DIR = "/codeInsight/overrideImplement/";

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }

  public void testImplementExtensionMethods() { doTest(true); }
  public void testOverrideExtensionMethods() { doTest(false); }
  public void testDoNotImplementExtensionMethods() { doTest(true); }
  public void testSkipUnknownAnnotations() { doTest(true); }

  public void testOverrideInInterface() { doTest(false); }
  public void testImplementInInterface() {
    myFixture.addClass """
interface A {
    void foo();
}
"""
    def file = myFixture.addClass("""
interface B extends A {
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    def implementMethodsName = ActionsBundle.message("action.ImplementMethods.text")
    final Presentation presentation = new Presentation();
    presentation.setText(implementMethodsName);

    CommandProcessor.instance.executeCommand(project, new Runnable() {
      @Override
      void run() {
        invokeAction(true)
      }
    }, presentation.getText(), null)
    

    myFixture.checkResult """
interface B extends A {
    @Override
    default void foo() {
        <caret>
    }
}
"""

  }
  public void testMultipleInheritedThrows() {doTest(false);}

  public void "test overriding overloaded method"() {
    myFixture.addClass """package bar;
interface A {
    void foo(Foo2 f);
    void foo(Foo1 f);
}
"""
    myFixture.addClass "package bar; class Foo1 {}"
    myFixture.addClass "package bar; class Foo2 {}"
    def file = myFixture.addClass("""package bar;
class Test implements A {
    public void foo(Foo1 f) {}
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    
    invokeAction(true)
    
    myFixture.checkResult """package bar;
class Test implements A {
    public void foo(Foo1 f) {}

    @Override
    public void foo(Foo2 f) {
        <caret>
    }
}
"""
  }

  private void doTest(boolean toImplement) {
    String name = getTestName(false);
    myFixture.configureByFile(BASE_DIR + "before" + name + ".java");
    invokeAction(toImplement)
    myFixture.checkResultByFile(BASE_DIR + "after" + name + ".java");
  }

  private void invokeAction(boolean toImplement) {
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiClass psiClass = PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), offset, PsiClass.class, false);
    assert psiClass != null;
    OverrideImplementUtil.chooseAndOverrideOrImplementMethods(getProject(), myFixture.getEditor(), psiClass, toImplement);
  }
}