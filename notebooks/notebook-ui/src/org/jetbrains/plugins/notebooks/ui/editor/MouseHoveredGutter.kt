package org.jetbrains.plugins.notebooks.ui.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

private val key = Key.create<Int?>(Int::class.java.name)

var Editor.notebookGutterHoverLine: Int?
  get() = key.get(this)
  set(value) = key.set(this, value)
