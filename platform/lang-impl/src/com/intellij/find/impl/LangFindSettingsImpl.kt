// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.EDT
import fleet.rpc.client.RpcTimeoutException

private val LOG = logger<LangFindSettingsImpl>()

internal class LangFindSettingsImpl: FindSettingsImpl() {
  override fun noStateLoaded() {
    if (EDT.isCurrentThreadEdt()) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), FindBundle.message("find.lang.extensions.loading")) {
        loadExtensions()
      }
    } else {
      runBlockingCancellable {
        loadExtensions()
      }
    }
  }

  private suspend fun loadExtensions() {
    val extensions =
    try {
      IdeLanguageCustomizationApi.getInstance().getPrimaryIdeLanguagesExtensions().toMutableSet()
    } catch (e: RpcTimeoutException) {
      LOG.error("Cannot get primary IDE languages extensions for FindInProjectSettingsBase initialization", e)
      return
    }
    if (extensions.contains("java")) {
      extensions.add("properties")
      extensions.add("jsp")
    }
    if (!extensions.contains("sql")) {
      extensions.add("xml")
      extensions.add("html")
      extensions.add("css")
    }
    if (extensions.contains("py")) {
      extensions.add("ipynb")
      extensions.add("pyi")
      extensions.add("pyx")
      extensions.add("pxd")
      extensions.add("pxi")
    }

    val extensionsArray = ArrayUtil.toStringArray(extensions)
    for (i in extensionsArray.indices.reversed()) {
      FindInProjectSettingsBase.addRecentStringToList("*." + extensionsArray[i], recentFileMasks)
    }
  }
}
