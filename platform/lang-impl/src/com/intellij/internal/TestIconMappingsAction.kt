// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ExperimentalUIImpl

/**
 * @author Konstantin Bulenkov
 */
class TestIconMappingsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val errors = mutableListOf<String>()
    val mappings = ExperimentalUIImpl.loadIconMappingsImpl()
    mappings.forEach { (classLoader, map) ->
      map.forEach { (expUI, oldUI) ->
        listOf(expUI, oldUI).forEach {
          if (!(it.endsWith(".svg") || it.endsWith(".png"))) {
            errors.add("Path should end with .svg or .png '$it'")
          }
          if (IconLoader.findIcon(it, classLoader)!!.iconHeight == 1) {
            errors.add("$it is not found")
          }
        }
      }
    }
    if (errors.isNotEmpty()) {
      Messages.showErrorDialog(errors.joinToString(separator = "\n") { it }, "Errors Found")
    }
    else {
      Messages.showInfoMessage("Everything is correct!", "Information")
    }
  }
}