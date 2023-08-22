// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Consumer
import java.awt.Component
import javax.swing.JComponent

class EnvFilesDialog(parentComponent: JComponent,
                     envFilePaths: MutableList<String>): DialogWrapper(parentComponent, true) {
  private val model = CollectionListModel(envFilePaths)
  private val list = JBList(model)
  init {
    title = ExecutionBundle.message("dialog.title.var.files")
    init()
    list.selectedIndex = 0
  }
  override fun createCenterPanel(): DialogPanel {
    val component = ToolbarDecorator.createDecorator(list).setAddAction {
      addEnvFile(list) { model.add(it) }
    }.createPanel()
    return panel {
      row {
        cell(component).align(Align.FILL)
      }
    }
  }

  override fun getPreferredFocusedComponent() = list

  val paths: List<String> = model.items
}

fun addEnvFile(component: Component, consumer: (s: String) -> Unit) {
  FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleLocalFileDescriptor(), null, component, null,
                         Consumer { consumer(it.path) })
}