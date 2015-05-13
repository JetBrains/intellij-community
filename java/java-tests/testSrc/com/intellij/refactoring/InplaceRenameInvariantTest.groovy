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
package com.intellij.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.LightCodeInsightTestCase

/**
 * User: anna
 */
class InplaceRenameInvariantTest extends LightCodeInsightTestCase {
  public void "test start caret position"() {
    def text = """\
     class <caret>Test {
     }
   }
   """

    doTestPositionInvariance(text, false, false)
  }

  public void "test middle caret position"() {
    def text = """\
      class Te<caret>st {
      }
    }
    """

    doTestPositionInvariance(text, false, false)
  }

  public void "test end caret position"() {
    def text = """\
      class Test<caret> {
      }
    }
    """

    doTestPositionInvariance(text, false, false)
  }

  public void "test end caret position typing"() {
    def text = """\
       class Test {
         Test<caret> myTest;
       }
     }
     """

    doTestPositionInvariance(text, false, false)
  }


  public void "test start caret position preselect"() {
    def text = """\
       class <caret>Test {
       }
     }
     """

    doTestPositionInvariance(text, true, false)
  }

  public void "test middle caret position preselect"() {
    def text = """\
        class Te<caret>st {
        }
      }
      """

    doTestPositionInvariance(text, true, false)
  }

  public void "test end caret position preselect"() {
    def text = """\
        class Test<caret> {
        }
      }
      """

    doTestPositionInvariance(text, true, false)
  }

  private doTestPositionInvariance(String text, final boolean preselect, final boolean checkTyping) {
    configure text
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(project)
    def oldPreselectSetting = myEditor.settings.preselectRename
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      myEditor.settings.preselectRename = preselect;
      int offset = myEditor.caretModel.offset
      final PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.getInstance().getAllAccepted())

      assertNotNull(element)

      MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler()


      handler.doRename(element, editor, null);
      
      if (checkTyping){
        type '1'
        offset++
      }

      assertEquals(offset, myEditor.caretModel.offset)
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