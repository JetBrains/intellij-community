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
package com.intellij.refactoring
import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.LightCodeInsightTestCase
/**
 * User: anna
 */
class RenameSuggestionsTest extends LightCodeInsightTestCase {
  public void "test by parameter name"() {
    def text = """\
     class Test {
         void foo(int foo) {}
         {
             int bar = 0;
             foo(b<caret>ar);
         }
     }
   }
   """

    doTestSuggestionAvailable(text, "foo")
  }

  public void "test by super parameter name"() {
    def text = """\
     class Test {
         void foo(int foo) {}
     }
     
     class TestImpl extends Test {
         void foo(int foo<caret>1) {}
     }
   }
   """

    doTestSuggestionAvailable(text, "foo")
  }

  private doTestSuggestionAvailable(String text, String suggestion) {
    configure text
    def oldPreselectSetting = myEditor.settings.preselectRename
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      final PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.getInstance().getAllAccepted())

      assertNotNull(element)

      VariableInplaceRenameHandler handler = new VariableInplaceRenameHandler()


      handler.doRename(element, editor, null);
      
      LookupEx lookup = LookupManager.getActiveLookup(editor)
      assertNotNull(lookup)
      boolean found = false;
      for (LookupElement item : lookup.items) {
        if (item.getLookupString().equals(suggestion)) {
          found = true
          break
        }
      }

      if (!found) {
        fail(suggestion + " not suggested")
      }
    }
    catch (Exception e) {
      e.printStackTrace()
    }
    finally {
      myEditor.settings.preselectRename = oldPreselectSetting

      TemplateState state = TemplateManagerImpl.getTemplateState(editor)

      assertNotNull(state)

      state.gotoEnd(false)
    }
  }

  private def configure(String text) {
    configureFromFileText("a.java", text)
  }
}