// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ExternalSystemUiUtils")

package com.intellij.openapi.externalSystem.service.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.io.FileUtil
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent


fun JTextComponent.isTextUnderMouse(e: MouseEvent): Boolean {
  val position = viewToModel2D(e.point)
  return position in 1 until text.length
}

fun getKeyStrokes(vararg actionIds: String): List<KeyStroke> {
  val keymapManager = KeymapManager.getInstance()
  val activeKeymap = keymapManager.activeKeymap
  return actionIds.asSequence()
    .flatMap { activeKeymap.getShortcuts(it).asSequence() }
    .filterIsInstance<KeyboardShortcut>()
    .flatMap { sequenceOf(it.firstKeyStroke, it.secondKeyStroke) }
    .filterNotNull()
    .toList()
}

fun JComponent.addKeyboardAction(keyStrokes: List<KeyStroke>, action: (ActionEvent) -> Unit) {
  for (keyStroke in keyStrokes) {
    registerKeyboardAction(action, keyStroke, JComponent.WHEN_FOCUSED)
  }
}

fun getUiPath(path: String): String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

fun getModelPath(path: String, removeLastSlash: Boolean = true): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}
