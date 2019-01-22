// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.BundleState.*
import com.intellij.bootRuntime.bundles.Runtime
import com.intellij.bootRuntime.command.*
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.SwingUtilities

class Controller(val actionPanel:ActionPanel, val model: Model) {


  init {
    runtimeSelected(model.selectedBundle)
  }

  // the fun is supposed to be invoked on the combobox selection
  fun runtimeSelected(runtime:Runtime) {
    model.updateBundle(runtime)
    actionPanel.removeAll()
    runtimeStateToActions(runtime, model.currentState())
      .map { abstractAction -> JButton(abstractAction) }
      .forEach{ button -> actionPanel.add(button) }
    SwingUtilities.getWindowAncestor(actionPanel)?.pack()
  }

  private fun runtimeStateToActions(runtime:Runtime, currentState: BundleState) : List<AbstractAction> {
    return when (currentState) {
      REMOTE -> listOf(Download(runtime))
      DOWNLOADED -> listOf(Extract(runtime), Delete(runtime))
      EXTRACTED -> listOf(Copy(runtime), Delete(runtime))
      INSTALLED -> listOf(UpdatePath(runtime), Delete(runtime))
    }
  }
}
