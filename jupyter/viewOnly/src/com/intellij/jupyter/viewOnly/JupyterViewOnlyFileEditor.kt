// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jupyter.viewOnly


import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import com.intellij.jupyter.core.jupyter.preview.JupyterCefHttpHandlerBase
import com.intellij.jupyter.core.jupyter.preview.addPathSegment
import java.beans.PropertyChangeListener
import java.nio.ByteBuffer
import javax.swing.JComponent

/**
 * Show jupyter file as web page in viewOnly mode
 */
class JupyterViewOnlyFileEditor private constructor(val myFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  // OSR is slower but doesn't work on Linux when component detached from Swing (i.e. tab switched)
  private val browser = JBCefBrowser.createBuilder().setOffScreenRendering(SystemInfo.isLinux).setUrl(
    JupyterCefHttpHandlerBase.getJupyterHttpUrl().addPathSegment("index.html").toString()).build()
  private val browserComponent: JComponent = browser.component
  private val darcula: MutableStateFlow<Boolean> = MutableStateFlow(UIUtil.isUnderDarcula())
  private val scope = CoroutineScope(Dispatchers.EDT)

  companion object {
    fun create(file: VirtualFile): JupyterViewOnlyFileEditor = JupyterViewOnlyFileEditor(file).apply {
      Disposer.register(this, browser)
      browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
          val str = Charsets.UTF_8.decode(ByteBuffer.wrap(myFile.contentsToByteArray())).toString()
          configureLaf()
          frame.executeJavaScript("const data=$str;", browser.url, 0)
          frame.executeJavaScript("render(data);", browser.url, 0)
        }
      }, browser.cefBrowser)
      browser.setOpenLinksInExternalBrowser(true)
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(LafManagerListener.TOPIC, LafManagerListener {
        // May be called several times, buffer merges calls
        darcula.value = UIUtil.isUnderDarcula()
      })
      scope.launch {
        darcula.collect {
          configureLaf()
        }
      }

    }
  }

  private fun configureLaf() {
    val theme = if (darcula.value) "dark" else "idea"
    browser.cefBrowser.executeJavaScript("IPythonRenderer.setTheme('$theme')", browser.cefBrowser.url, 0)
  }

  override fun getComponent(): JComponent = browserComponent

  override fun getPreferredFocusedComponent(): JComponent = browserComponent

  override fun getName(): String = myFile.name

  override fun getFile(): VirtualFile = myFile

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun dispose() {
    scope.cancel()
  }
}
