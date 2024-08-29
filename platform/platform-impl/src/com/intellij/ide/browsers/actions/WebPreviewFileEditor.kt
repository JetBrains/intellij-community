// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.ReloadMode
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.GotItTooltip
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.ide.BuiltInServerBundle
import java.awt.Color
import java.awt.Point
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent

@Internal
val CUSTOM_ORIGINAL_FILE = Key.create<VirtualFile>("custom.original.file")
private const val WEB_PREVIEW_RELOAD_TOOLTIP_ID: String = "web.preview.reload.on.save"

@Internal
class WebPreviewFileEditor internal constructor(file: WebPreviewVirtualFile) : UserDataHolderBase(), FileEditor {
  private val file = file.getUserData(CUSTOM_ORIGINAL_FILE) ?: file.originalFile
  private val panel: JCEFHtmlPanel
  private val url = file.previewUrl.toExternalForm()

  @Deprecated("Use {@link #WebPreviewFileEditor(WebPreviewVirtualFile)}", level = DeprecationLevel.ERROR)
  constructor(project: Project, file: WebPreviewVirtualFile) : this(file)

  init {
    panel = object : JCEFHtmlPanel(url) {
      override fun getBackgroundColor(): Color = Color.WHITE
    }
  }

  companion object {
    private val previewsOpened = AtomicInteger()

    val isPreviewOpened: Boolean
      get() = previewsOpened.get() > 0
  }

  internal fun reloadPage() {
    panel.loadURL(url)
    previewsOpened.incrementAndGet()
    showPreviewTooltip()
  }

  private fun showPreviewTooltip() {
    ApplicationManager.getApplication().invokeLater {
      val gotItTooltip = GotItTooltip(WEB_PREVIEW_RELOAD_TOOLTIP_ID, BuiltInServerBundle.message("reload.on.save.preview.got.it.content"),
                                      this)
      if (!gotItTooltip.canShow()) {
        return@invokeLater
      }

      if (WebBrowserManager.PREVIEW_RELOAD_MODE_DEFAULT != ReloadMode.RELOAD_ON_SAVE) {
        logger<WebPreviewFileEditor>().error(
          "Default value for " + BuiltInServerBundle.message("reload.on.save.preview.got.it.title") + " has changed, tooltip is outdated.")
        return@invokeLater
      }
      if (WebBrowserManager.getInstance().webPreviewReloadMode != ReloadMode.RELOAD_ON_SAVE) {
        // changed before gotIt was shown
        return@invokeLater
      }

      gotItTooltip
        .withHeader(BuiltInServerBundle.message("reload.on.save.preview.got.it.title"))
        .withPosition(Balloon.Position.above)
        .withLink(CommonBundle.message("action.text.configure.ellipsis"), Runnable {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, { it: Configurable? ->
            it is SearchableConfigurable &&
            it.id == "reference.settings.ide.settings.web.browsers"
          }, null)
        })
      gotItTooltip.show(panel.component) { _, _ -> Point(0, 0) }
    }
  }

  override fun getComponent(): JComponent = panel.component

  override fun getPreferredFocusedComponent() = panel.component

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return IdeBundle.message("web.preview.file.editor.name", file.name)
  }

  override fun setState(state: FileEditorState) {
  }

  override fun getFile(): VirtualFile = file

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun dispose() {
    previewsOpened.decrementAndGet()
    Disposer.dispose(panel)
  }
}
