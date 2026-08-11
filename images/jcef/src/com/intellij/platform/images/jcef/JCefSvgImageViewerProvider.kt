// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.images.jcef

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import org.intellij.images.editor.impl.SvgImageViewerProvider

internal class JCefSvgImageViewerProvider : SvgImageViewerProvider {
  override fun createEditor(file: VirtualFile): FileEditor? {
    if (!JBCefApp.isSupported() || !RegistryManager.getInstance().`is`("ide.browser.jcef.svg-viewer.enabled")) {
      return null
    }
    return JCefImageViewer(file, "image/svg+xml")
  }
}
