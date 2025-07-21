// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options

import com.intellij.application.options.editor.AutoImportOptionsConfigurable
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Panel

public class JavaCompletionConfigurable : UiDslUnnamedConfigurable.Simple() {

  override fun Panel.createContent() {
    row {
      link(JavaBundle.message("link.configure.classes.excluded.from.completion")) {
        val dataContext = DataManager.getInstance().getDataContext(it.source as ActionLink)
        val settingsEditor = dataContext.getData(Settings.KEY) ?: return@link
        val configurable = settingsEditor.find(AutoImportOptionsConfigurable::class.java) ?: return@link
        settingsEditor.select(configurable, JavaBundle.message("exclude.from.completion.group"))
      }
    }
  }
}
