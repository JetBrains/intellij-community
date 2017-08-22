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
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil

/**
 * @author ven
 */
class CreateMethodFromUsageTest extends LightQuickFixTestCase {
  void test() throws Exception { doAllTests() }

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
    doAction("Create method 'addSubGroup'")
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
    doAction("Create method 'getInstance'")
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
    doAction("Create method 'getInstance'")
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
    doAction("Create method 'foo'")
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
class A {
    int x;
    A(int x) { this.x = x; }
    class B extends A{
        B(int x) { super(foo(x)); }
    }

    private int foo(int x) {
        return 0;
    }
}
"""

  }

  void "test use fully qualified names with conflicting imports"() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(project);
    def fqClassNames = settings.useFqClassNames
    try {
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
      doAction("Create method 'foo'")
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
    finally {
      settings.setUseFqClassNames(fqClassNames)
    }

  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage"
  }

}
