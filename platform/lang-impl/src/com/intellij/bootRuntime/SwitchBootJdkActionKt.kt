// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.bundles.Runtime
import com.intellij.bootRuntime.command.CommandFactory
import com.intellij.bootRuntime.ui.dialog
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.*
import java.awt.GridLayout
import javax.swing.*

/**
 * @author denis
 */
class SwitchBootJdkAction : AnAction(), DumbAware {

  var actions:List<Action> = emptyList()

  override fun actionPerformed(e: AnActionEvent) {

    val localBundles = RuntimeLocationsFactory().localBundles(e.project!!)
    val bintrayBundles = RuntimeLocationsFactory().bintrayBundles(e.project!!)


    // todo change to dsl
    val southPanel = ActionPanel()

    val controller = Controller(e.project!!, southPanel, Model(localBundles.get(0), localBundles + bintrayBundles))



    val repositoryUrlFieldSpinner = JLabel(AnimatedIcon.Default())
    repositoryUrlFieldSpinner.isVisible = false

    val combobox = ComboBox<Runtime>()

    val myRuntimeUrlComboboxModel = CollectionComboBoxModel<Runtime>()

    val myRuntimeUrlField = TextFieldWithAutoCompletion<Runtime>(e.project,
                                                                 RuntimeCompletionProvider(localBundles + bintrayBundles),
                                                                 false,
                                                                 "")

    combobox.isEditable = true
    combobox.editor = ComboBoxCompositeEditor.
      withComponents<Any,TextFieldWithAutoCompletion<Runtime>>(myRuntimeUrlField, repositoryUrlFieldSpinner)


    localBundles.let{ bundle -> myRuntimeUrlComboboxModel.add(bundle) }
    bintrayBundles.let{ bundle -> myRuntimeUrlComboboxModel.add(bundle)}

    combobox.model = myRuntimeUrlComboboxModel

    // todo change to dsl
    val centralPanel = JPanel(GridLayout(1,1))
    centralPanel.add(combobox)

    myRuntimeUrlField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        localBundles.firstOrNull { it.toString() == myRuntimeUrlField.text }?.let { match -> controller.runtimeSelected(match)}
        bintrayBundles.firstOrNull { it.toString() == myRuntimeUrlField.text }?.let { match -> controller.runtimeSelected(match)}
      }
    })

    dialog(
      title = "Switch Boot Runtime",
      owner = WindowManager.getInstance().suggestParentWindow(e.project),
      centerPanel = centralPanel,
      southPanel = southPanel
    )
  }
}

class RuntimeCompletionProvider(variants: Collection<Runtime>?) : TextFieldWithAutoCompletionListProvider<Runtime>(
  variants), DumbAware {
  override fun getLookupString(item: Runtime): String {
    return item.toString()
  }

  override fun compare(r1: Runtime, r2: Runtime): Int {
    return StringUtil.compare(r1.toString(), r2.toString(), false)
  }
}