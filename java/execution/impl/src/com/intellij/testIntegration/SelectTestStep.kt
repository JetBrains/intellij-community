/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration

import com.intellij.execution.testframework.TestIconMapper
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.PsiNavigateUtil
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.KeyStroke

class RecentTestsListPopup(popupStep: ListPopupStep<RecentTestsPopupEntry>,
                           private val testRunner: RecentTestRunner,
                           private val locator: TestLocator) 
    : ListPopupImpl(popupStep) 
{

  init {
    shiftReleased()
    registerActions(this)

    val shift = if (SystemInfo.isMac) MacKeymapUtil.SHIFT else "Shift"
    setAdText("Debug with $shift, navigate with F4")
  }

  private fun registerActions(popup: ListPopupImpl) {
    popup.registerAction("alternate", KeyStroke.getKeyStroke("shift pressed SHIFT"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = shiftPressed()
    })
    popup.registerAction("restoreDefault", KeyStroke.getKeyStroke("released SHIFT"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = shiftReleased()
    })
    popup.registerAction("invokeAction", KeyStroke.getKeyStroke("shift ENTER"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = handleSelect(true)
    })
    
    popup.registerAction("navigate", KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        val values = selectedValues
        if (values.size == 1) {
          val element = (values[0] as RecentTestsPopupEntry).navigatableElement(locator)
          if (element != null) {
            cancel()
            PsiNavigateUtil.navigate(element)
          }
        }
      }
    })
  }

  private fun shiftPressed() {
    setCaption("Debug Recent Tests")
    testRunner.setMode(RecentTestRunner.Mode.DEBUG)
  }

  private fun shiftReleased() {
    setCaption("Run Recent Tests")
    testRunner.setMode(RecentTestRunner.Mode.RUN)
  }
}


class SelectTestStep(tests: List<RecentTestsPopupEntry>, 
                     private val runner: RecentTestRunner) 
     : BaseListPopupStep<RecentTestsPopupEntry>("Debug Recent Tests", tests) 
{

  override fun getIconFor(value: RecentTestsPopupEntry): Icon? {
    return TestIconMapper.getIcon(value.magnitude)
  }

  override fun getTextFor(value: RecentTestsPopupEntry) = value.presentation
  
  override fun isSpeedSearchEnabled() = true

  override fun onChosen(entry: RecentTestsPopupEntry, finalChoice: Boolean): PopupStep<RecentTestsPopupEntry>? {
    entry.run(runner)
    return null
  }

}
