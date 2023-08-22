// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import groovy.transform.CompileStatic 
/**
 * User: anna
 */
@CompileStatic
class InplaceRenameInvariantTest extends LightJavaCodeInsightTestCase {
  void "test start caret position"() {
    def text = """\
     class <caret>Test {
     }
   }
   """

    doTestPositionInvariance(text, false, false)
  }

  void "test middle caret position"() {
    def text = """\
      class Te<caret>st {
      }
    }
    """

    doTestPositionInvariance(text, false, false)
  }

  void "test end caret position"() {
    def text = """\
      class Test<caret> {
      }
    }
    """

    doTestPositionInvariance(text, false, false)
  }

  void "test end caret position typing"() {
    def text = """\
       class Test {
         Test<caret> myTest;
       }
     }
     """

    doTestPositionInvariance(text, false, false)
  }


  void "test start caret position preselect"() {
    def text = """\
       class <caret>Test {
       }
     }
     """

    doTestPositionInvariance(text, true, false)
  }

  void "test middle caret position preselect"() {
    def text = """\
        class Te<caret>st {
        }
      }
      """

    doTestPositionInvariance(text, true, false)
  }

  void "test end caret position preselect"() {
    def text = """\
        class Test<caret> {
        }
      }
      """

    doTestPositionInvariance(text, true, false)
  }

  private doTestPositionInvariance(String text, final boolean preselect, final boolean checkTyping) {
    configure text
    def oldPreselectSetting = editor.settings.preselectRename
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable())
      editor.settings.preselectRename = preselect
      int offset = editor.caretModel.offset
      final PsiElement element = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted())

      assertNotNull(element)

      MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler()


      handler.doRename(element, editor, null)
      
      if (checkTyping){
        type '1'
        offset++
      }

      assertEquals(offset, editor.caretModel.offset)
    }
    finally {
      editor.settings.preselectRename = oldPreselectSetting

      TemplateState state = TemplateManagerImpl.getTemplateState(editor)

      assertNotNull(state)

      state.gotoEnd(false)
    }
  }

  private def configure(String text) {
    configureFromFileText("a.java", text)
  }
}