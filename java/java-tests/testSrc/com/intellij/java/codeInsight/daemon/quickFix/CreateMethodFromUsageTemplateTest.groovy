// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix


import com.intellij.codeInsight.ExpectedTypesProvider
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil

/**
 * @author ven
 */
class CreateMethodFromUsageTemplateTest extends LightQuickFixTestCase {

  void testTemplateAssertions() throws Exception {
    configureFromFileText "a.java", """
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
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    doAction("Create method 'addSubGroup' in 'Group in PropertyDescriptorWithVeryLongName in SomeOuterClassWithLongName'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())
    //skip void return type
    state.nextTab()

    // parameter type
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString?.endsWith('Group')

    EditorActionManager actionManager = EditorActionManager.getInstance()
    final DataContext dataContext = DataManager.getInstance().getDataContext()
    actionManager.getActionHandler(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM).execute(getEditor(), dataContext)

    // parameter name, skip it
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString == 'child'
    state.nextTab()

    assert state.finished

    checkResultByText """
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

  void "test prefer nearby return types"() {
    configureFromFileText "a.java", """
class Singleton {
    boolean add(Object o) {}

}
class Usage {
    void foo() {
        Singleton.get<caret>Instance().add("a");
    }

}
"""
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    doAction("Create method 'getInstance' in 'Singleton'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())
    // parameter type
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString == 'Singleton'
  }

  void "test delete created modifiers"() {
    configureFromFileText "a.java", """
interface Singleton {
    default boolean add(Object o) {}

}
class Usage {
    void foo() {
        Singleton.get<caret>Instance().add("a");
    }

}
"""
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    doAction("Create method 'getInstance' in 'Singleton'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())

    def document = getEditor().getDocument()
    def offset = getEditor().getCaretModel().getOffset()
    
    ApplicationManager.application.runWriteAction {
      def method = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiMethod.class)
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, false)
      PsiDocumentManager.getInstance(getFile().project).commitDocument(document)
    }
    
    state.gotoEnd(false)
  }

  void "test prefer outer class when static is not applicable for inner"() {
    configureFromFileText "a.java", """
class A {
    int x;
    A(int x) { this.x = x; }
    class B extends A{
        B(int x) { super(f<caret>oo(x)); }
    }
}
"""
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    doAction("Create method 'foo' in 'A'")
    TemplateManagerImpl.getTemplateState(getEditor()).gotoEnd(false)

    checkResultByText """
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
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(project);
    settings.setUseFqClassNames(true)
    configureFromFileText "a.java", """
import java.awt.List;
class A {
    void m(java.util.List<String> list){
      fo<caret>o(list);
    }
}
"""
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
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

    checkResultByText """
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
    configureFromFileText "a.java", """
  /**javadoc*/
  class A {
      void m(java.util.List<String> list){
        fo<caret>o(list);
      }
  }
  """
      TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
      doAction("Create method 'foo' in 'A'")
      def state = TemplateManagerImpl.getTemplateState(getEditor())
    
      state.gotoEnd(false)

      checkResultByText """import java.util.List;

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
    configureFromFileText 'a.java', '''\
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
    checkResultByText '''\
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
    configureFromFileText 'InvalidClass.java', '''\
public class InvalidClass {
    void usage() {
        <caret>getFoo();
    }
'''

    TemplateManagerImpl.setTemplateTesting project, testRootDisposable
    doAction "Create read-only property 'foo' in 'InvalidClass'"
    TemplateManagerImpl.getTemplateState editor gotoEnd false

    checkResultByText '''\
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
    configureFromFileText 'a.java', '''\
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
}
