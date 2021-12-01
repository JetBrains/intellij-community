/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.ui.UIUtil
import javax.swing.plaf.PanelUI

class ColoredTextConsole(project: Project, viewer: Boolean = false) :
  ConsoleViewImpl(project, viewer), AnsiEscapeDecoder.ColoredTextAcceptor {

  private val ansiEscapeDecoder = AnsiEscapeDecoder()

  // when it's true its save to call editor, otherwise call 'editor' will throw an NPE
  private val objectInitialized = true;

  fun addData(message: String, outputType: Key<*>) {
    ansiEscapeDecoder.escapeText(message, outputType, this)
  }

  override fun coloredTextAvailable(text: String, attributes: Key<*>) {
    print(text, ConsoleViewContentType.getConsoleViewType(attributes))
  }

  override fun setUI(ui: PanelUI?) {
    super.setUI(ui)
    if (objectInitialized && editor != null) {
      (editor as EditorImpl).backgroundColor = UIUtil.getPanelBackground()
    }
  }
}
