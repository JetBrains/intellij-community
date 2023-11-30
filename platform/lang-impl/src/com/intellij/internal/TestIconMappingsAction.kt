// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.ui.IconMapLoader
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.platform.ide.progress.ModalTaskOwner

/**
 * @author Konstantin Bulenkov
 */
private class TestIconMappingsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val errors = mutableListOf<String>()
    val mappings = runWithModalProgressBlocking(e.project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(), "") {
      service<IconMapLoader>().doLoadIconMapping()
    }
    for ((classLoader, map) in mappings) {
      for ((expUI, oldUI) in map) {
        for (filePath in listOf(expUI, oldUI)) {
          when {
            filePath.isBlank() -> errors.add("$filePath is empty (expUI=$expUI, oldUI=$oldUI)")
            !(filePath.endsWith(".svg") || filePath.endsWith(".png")) -> errors.add("Path should end with .svg or .png '$filePath'")
            findIconUsingNewImplementation(filePath, classLoader)!!.iconHeight == 1 -> errors.add("$filePath is not found")
          }
        }
      }
    }
    if (errors.isEmpty()) {
      Messages.showInfoMessage("Everything is correct!", "Information")
    }
    else {
      Messages.showErrorDialog(errors.joinToString(separator = "\n") { it }, "Errors Found")
    }
  }
}