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

import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiElement
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.PsiNavigateUtil
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.KeyStroke


class RecentTestsListPopup(popupStep: ListPopupStep<RecentTestsPopupEntry>,
                           private val testRunner: RecentTestRunner,
                           private val locator: TestLocator) : ListPopupImpl(popupStep) {

  init {
    shiftReleased()
    registerActions(this)

    val shift = if (SystemInfo.isMac) MacKeymapUtil.SHIFT else "Shift"
    setAdText("Debug with $shift, navigate with F4")
  }

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    val popup = super.createPopup(parent, step, parentValue)
    registerActions(popup)
    return popup
  }

  private fun registerActions(popup: WizardPopup) {
    popup.onShiftPressed { shiftPressed() }
    popup.onShiftReleased { shiftReleased() }

    if (popup is ListPopupImpl) {
      popup.selectOnShiftEnter()
      popup.navigateOnF4(locator, this)
    }
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


class SelectTestStep(title: String?,
                     tests: List<RecentTestsPopupEntry>, 
                     private val runner: RecentTestRunner) : BaseListPopupStep<RecentTestsPopupEntry>(title, tests) 
{

  override fun getIconFor(value: RecentTestsPopupEntry): Icon? {
    if (value is SingleTestEntry) {
      return AllIcons.RunConfigurations.TestFailed  
    }
    else {
      return AllIcons.RunConfigurations.TestPassed
    }
  }

  override fun getTextFor(value: RecentTestsPopupEntry): String = value.presentation
  
  override fun isSpeedSearchEnabled(): Boolean = true

  override fun hasSubstep(selectedValue: RecentTestsPopupEntry): Boolean = getConfigurations(selectedValue).isNotEmpty()

  override fun onChosen(entry: RecentTestsPopupEntry, finalChoice: Boolean): PopupStep<RecentTestsPopupEntry>? {
    if (finalChoice) {
      runner.run(entry)
      return null
    }

    val configurations = getConfigurations(entry)
    return SelectConfigurationStep(configurations, runner)
  }

  private fun getConfigurations(entry: RecentTestsPopupEntry): List<RecentTestsPopupEntry> {
    val collector = TestConfigurationCollector()
    entry.accept(collector)
    return collector.getEnclosingConfigurations()
  }

}


class SelectConfigurationStep(items: List<RecentTestsPopupEntry>,
                              private val runner: RecentTestRunner) : BaseListPopupStep<RecentTestsPopupEntry>(null, items) {

  override fun getTextFor(value: RecentTestsPopupEntry): String {
    var presentation = value.presentation
    value.accept(object : TestEntryVisitor() {
      override fun visitSuite(suite: SuiteEntry) {
        presentation = "[suite] " + presentation
      }

      override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
        presentation = "[configuration] " + presentation
      }
    })
    return presentation
  } 

  override fun getIconFor(value: RecentTestsPopupEntry?): Icon? = AllIcons.RunConfigurations.Junit

  override fun onChosen(selectedValue: RecentTestsPopupEntry, finalChoice: Boolean): PopupStep<RecentTestsPopupEntry>? {
    if (finalChoice) {
      runner.run(selectedValue)
    }
    
    return null
  }
  
}


private fun WizardPopup.onShiftPressed(action: () -> Unit) {
  registerAction("alternate", KeyStroke.getKeyStroke("shift pressed SHIFT"), object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      action()
    }
  })
}

private fun WizardPopup.onShiftReleased(action: () -> Unit) {
  registerAction("restoreDefault", KeyStroke.getKeyStroke("released SHIFT"), object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      action()
    }
  })
}

private fun ListPopupImpl.selectOnShiftEnter() {
  registerAction("invokeAction", KeyStroke.getKeyStroke("shift ENTER"), object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      handleSelect(true)
    }
  })
}

private fun ListPopupImpl.navigateOnF4(locator: TestLocator, parentPopup: RecentTestsListPopup) {
  registerAction("navigate", KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      val values = selectedValues
      if (values.size == 1) {
        val entry = values[0] as RecentTestsPopupEntry
        locator.getNavigatableElement(entry)?.let { 
          parentPopup.cancel()
          PsiNavigateUtil.navigate(it) 
        }
      }
    }
  })
}

private fun TestLocator.getNavigatableElement(entry: RecentTestsPopupEntry): PsiElement? {
  var element: PsiElement? = null
  entry.accept(object : TestEntryVisitor() {
    override fun visitTest(test: SingleTestEntry) {
      element = getLocation(test.url)?.psiElement
    }

    override fun visitSuite(suite: SuiteEntry) {
      element = getLocation(suite.suiteUrl)?.psiElement
    }

    override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
      if (configuration.suites.size == 1) {
        visitSuite(configuration.suites[0])
      }
    }
  })
  return element
}