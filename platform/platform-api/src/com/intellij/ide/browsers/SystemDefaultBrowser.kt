// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import javax.swing.Icon

/**
 * The browser that the operating system opens for a URL.
 *
 * The launcher opens it with the desktop API or with the default command of the operating system.
 * It has no family, no path and no settings.
 * It is not in the browser list of `WebBrowserManager`, but `WebBrowserManager.findBrowserById` finds it by its [id][getId].
 */
@ApiStatus.Internal
object SystemDefaultBrowser : WebBrowser() {
  private val ID: UUID = UUID.fromString("5d4b3c8e-0f6a-4e1d-9b27-6c8a1f3e2d90")

  override fun getName(): String = IdeBundle.message("settings.browsers.system.default")

  override fun getId(): UUID = ID

  override fun getFamily(): BrowserFamily? = null

  override fun getIcon(): Icon = AllIcons.General.Web

  override fun getPath(): String? = null

  override fun getBrowserNotFoundMessage(): String = IdeBundle.message("browser.default.not.supported")

  override fun getSpecificSettings(): BrowserSpecificSettings? = null

  override fun toString(): String = "SystemDefaultBrowser"
}
