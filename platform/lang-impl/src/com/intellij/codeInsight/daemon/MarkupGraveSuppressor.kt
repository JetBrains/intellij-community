package com.intellij.codeInsight.daemon

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile


open class MarkupGraveSuppressor {
  open fun shouldSuppress(file: VirtualFile, document: Document): Boolean = false
}