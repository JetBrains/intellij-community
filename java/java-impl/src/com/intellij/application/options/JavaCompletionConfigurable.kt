// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options

import com.intellij.application.options.editor.AutoImportOptionsConfigurable
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.ActionLink
import javax.swing.JComponent

class JavaCompletionConfigurable : UnnamedConfigurable {
  override fun createComponent(): JComponent {
    return ActionLink(JavaBundle.message("link.configure.classes.excluded.from.completion")) {
      val dataContext = DataManager.getInstance().getDataContext(it.source as ActionLink)
      val settingsEditor = dataContext.getData(Settings.KEY) ?: return@ActionLink
      val configurable = settingsEditor.find(AutoImportOptionsConfigurable::class.java) ?: return@ActionLink
      settingsEditor.select(configurable, JavaBundle.message("exclude.from.completion.group"))
    }
  }

  override fun isModified(): Boolean = false

  override fun apply() {
  }
}

