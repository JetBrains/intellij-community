// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.ExpectedTypesProvider
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

/**
 * @author ven
 */
@CompileStatic
class CreateMethodFromUsageTemplateTest extends LightCodeInsightFixtureTestCase {

  void testTemplateAssertions() throws Exception {
    myFixture.configureByText "a.java", """
class SomeOuterClassWithLongName {
    void foo(PropertyDescriptorWithVeryLongName.Group group, PropertyDescriptorWithVeryLongName.Group child) {
        group.add<caret>SubGroup(child);
    }
    static class PropertyDescriptorWithVeryLongName {
        static class Group {

        }
    }
}
"""
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    doAction("Create method 'addSubGroup' in 'Group in PropertyDescriptorWithVeryLongName in SomeOuterClassWithLongName'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())
    //skip void return type
    state.nextTab()

    // parameter type
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString?.endsWith('Group')

    myFixture.type('\n')

    // parameter name, skip it
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString == 'child'
    state.nextTab()

    assert state.finished

    myFixture.checkResult """
class SomeOuterClassWithLongName {
    void foo(PropertyDescriptorWithVeryLongName.Group group, PropertyDescriptorWithVeryLongName.Group child) {
        group.addSubGroup(child);
    }
    static class PropertyDescriptorWithVeryLongName {
        static class Group {

            public void addSubGroup(Group child) {
                <selection></selection>
            }
        }
    }
}
"""

  }

  def doAction(String hint) {
    myFixture.launchAction(myFixture.findSingleIntention(hint))
  }

  void "test prefer nearby return types"() {
    myFixture.configureByText "a.java", """
class Singleton {
    boolean add(Object o) {}

}
class Usage {
    void foo() {
        Singleton.get<caret>Instance().add("a");
    }

}
"""
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    doAction("Create method 'getInstance' in 'Singleton'")
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString == 'Singleton'
  }

  void "test delete created modifiers"() {
    myFixture.configureByText "a.java", """
interface Singleton {
    default boolean add(Object o) {}

}
class Usage {
    void foo() {
        Singleton.get<caret>Instance().add("a");
    }

}
"""
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    doAction("Create method 'getInstance' in 'Singleton'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())

    def document = getEditor().getDocument()
    def offset = getEditor().getCaretModel().getOffset()

    WriteCommandAction.runWriteCommandAction(project) {
      def method = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiMethod.class)
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, false)
      PsiDocumentManager.getInstance(getFile().project).commitDocument(document)
    }
    
    state.gotoEnd(false)
  }

  void "test prefer outer class when static is not applicable for inner"() {
    myFixture.configureByText "a.java", """
class A {
    int x;
    A(int x) { this.x = x; }
    class B extends A{
        B(int x) { super(f<caret>oo(x)); }
    }
}
"""
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    doAction("Create method 'foo' in 'A'")
    TemplateManagerImpl.getTemplateState(getEditor()).gotoEnd(false)

    myFixture.checkResult """
class A {
    int x;
    A(int x) { this.x = x; }
    class B extends A{
        B(int x) { super(foo(x)); }
    }

    private static int foo(int x) {
        return 0;
    }
}
"""

  }

  void "test use fully qualified names with conflicting imports"() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(project)
    settings.setUseFqClassNames(true)
    myFixture.configureByText "a.java", """
import java.awt.List;
class A {
    void m(java.util.List<String> list){
      fo<caret>o(list);
    }
}
"""
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    doAction("Create method 'foo' in 'A'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())

    def document = getEditor().getDocument()
    def offset = getEditor().getCaretModel().getOffset()

    ApplicationManager.application.runWriteAction {
      def method = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiMethod.class)
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, false)
      PsiDocumentManager.getInstance(getFile().project).commitDocument(document)
    }

    state.gotoEnd(false)

    myFixture.checkResult """
import java.awt.List;
class A {
    void m(java.util.List<String> list){
      foo(list);
    }

    private void foo(java.util.List<String> list) {
        
    }
}
"""
  }

  void "test format adjusted imports"() {
    myFixture.configureByText "a.java", """
  /**javadoc*/
  class A {
      void m(java.util.List<String> list){
        fo<caret>o(list);
      }
  }
  """
      TemplateManagerImpl.setTemplateTesting(testRootDisposable)
      doAction("Create method 'foo' in 'A'")
      def state = TemplateManagerImpl.getTemplateState(getEditor())
    
      state.gotoEnd(false)

      myFixture.checkResult """import java.util.List;

/**javadoc*/
  class A {
      void m(java.util.List<String> list){
        foo(list);
      }

    private void foo(List<String> list) {
        
    }
  }
  """
  }

  void 'test guess type parameters'() {
    myFixture.configureByText 'a.java', '''\
public class A {
    void m(java.util.List<String> list, B<String> b) {
        b.<caret>foo(list);
    }
}

class B<T>
{
}
'''
    doAction("Create method 'foo' in 'B'")
    myFixture.checkResult '''\
import java.util.List;

public class A {
    void m(java.util.List<String> list, B<String> b) {
        b.foo(list);
    }
}

class B<T>
{
    public void foo(List<T> list) {
        <caret>
    }
}
'''
  }

  void 'test create property in invalid class'() {
    myFixture.configureByText 'InvalidClass.java', '''\
public class InvalidClass {
    void usage() {
        <caret>getFoo();
    }
'''

    TemplateManagerImpl.setTemplateTesting testRootDisposable
    doAction "Create read-only property 'foo' in 'InvalidClass'"
    TemplateManagerImpl.getTemplateState editor gotoEnd false

    myFixture.checkResult '''\
public class InvalidClass {
    private Object foo;

    void usage() {
        getFoo();
    }

    public Object getFoo() {<caret>
        return foo;
    }
'''
  }

  void 'test deepest super methods are included in expected info when available'() {
    myFixture.configureByText 'a.java', '''\
class A {
  {
    new A().get<caret>Bar().toString();
  }
}
'''
    def expr = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PsiExpression.class)

    def types = ExpectedTypesProvider.getExpectedTypes(expr, false)
    assertNotNull(types.find {it.defaultType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)})
  }

  void 'test typing generics'() {
    myFixture.configureByText 'a.java', '''\
class A {
    {
        pass<caret>Class(String.class)
    }
}
'''
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = true
    try {
      doAction "Create method 'passClass' in 'A'"
      myFixture.type('\t')
      myFixture.type('Class<?>\n')
      myFixture.checkResult '''\
class A {
    {
        passClass(String.class)
    }

    private void passClass(Class<?> <selection>stringClass</selection>) {
    }
}
'''
    }
    finally {
      CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = false
    }
  }
}
