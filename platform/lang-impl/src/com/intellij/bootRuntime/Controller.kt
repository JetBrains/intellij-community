// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.BundleState.*
import com.intellij.bootRuntime.bundles.Runtime
import com.intellij.bootRuntime.command.Command
import com.intellij.bootRuntime.command.CommandFactory
import com.intellij.bootRuntime.command.CommandFactory.Type.*
import com.intellij.bootRuntime.command.CommandFactory.produce
import com.intellij.openapi.project.Project
import javax.swing.JButton
import javax.swing.SwingUtilities

class Controller(val project: Project, val actionPanel:ActionPanel, val model: Model) {

  init {
    CommandFactory.initialize(project, this)
    runtimeSelected(model.selectedBundle)
  }

  // the fun is supposed to be invoked on the combobox selection
  fun runtimeSelected(runtime:Runtime) {
    model.updateBundle(runtime)
    actionPanel.removeAll()
    runtimeStateToActions(runtime, model.currentState())
      .map { abstractAction -> JButton(abstractAction) }
      .forEach{ button -> actionPanel.add(button) }
    actionPanel.repaint()
    SwingUtilities.getWindowAncestor(actionPanel)?.pack()
  }

  private fun runtimeStateToActions(runtime:Runtime, currentState: BundleState) : List<Command> {
    return when (currentState) {
      REMOTE -> listOf(produce(REMOTE_INSTALL, runtime))
      DOWNLOADED -> listOf(produce(INSTALL, runtime), produce(DELETE, runtime))
      EXTRACTED -> listOf(produce(INSTALL, runtime), produce(DELETE, runtime))
      UNINSTALLED -> listOf(produce(INSTALL, runtime), produce(DELETE, runtime))
      INSTALLED -> listOf(produce(UNINSTALL, runtime), produce(DELETE, runtime))
    }
  }
}
