package com.intellij.codeInsight.daemon.quickFix
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
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
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
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
                <selection>//To change body of created methods use File | Settings | File Templates.</selection>
            }
        }
    }
}
"""

  }

  @Override
  protected void tearDown() throws Exception {
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
    super.tearDown()
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}
