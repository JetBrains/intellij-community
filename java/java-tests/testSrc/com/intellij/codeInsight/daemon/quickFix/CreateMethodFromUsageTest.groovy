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
package com.intellij.codeInsight.daemon.quickFix
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
/**
 * @author ven
 */
public class CreateMethodFromUsageTest extends LightQuickFixTestCase {
  public void test() throws Exception { doAllTests(); }

  public void testTemplateAssertions() throws Exception {
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
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable);
    doAction("Create Method 'addSubGroup'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())
    //skip void return type
    state.nextTab()

    // parameter type
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString?.endsWith('Group')

    EditorActionManager actionManager = EditorActionManager.getInstance();
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    actionManager.getActionHandler(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM).execute(getEditor(), dataContext);

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

  public void "test prefer nearby return types"() {
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
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable);
    doAction("Create Method 'getInstance'")
    def state = TemplateManagerImpl.getTemplateState(getEditor())
    // parameter type
    assert LookupManager.getActiveLookup(editor)?.currentItem?.lookupString == 'Singleton'
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}
