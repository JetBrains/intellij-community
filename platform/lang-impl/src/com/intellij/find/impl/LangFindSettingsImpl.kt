// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindSettings
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.rpc.performRpcWithRetries
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ArrayUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LOG = logger<LangFindSettingsImpl>()

internal class LangFindSettingsImpl: FindSettingsImpl(), Disposable {
  private var languageExtensionsLoadingJob: Job? = null

  override fun noStateLoaded() {
    languageExtensionsLoadingJob = FindSettingsCoroutineScopeProvider.getInstance().coroutineScope.launch {
      loadExtensions()
    }
  }

  private suspend fun loadExtensions() {
    val extensions = LOG.performRpcWithRetries {
      IdeLanguageCustomizationApi.getInstance().getPrimaryIdeLanguagesExtensions().toMutableSet()
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

  override fun dispose() {
    languageExtensionsLoadingJob?.cancel()
  }
}

@Service(Service.Level.APP)
private class FindSettingsCoroutineScopeProvider(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): FindSettingsCoroutineScopeProvider = service()
  }
}

private class FindSettingsInitializer : ApplicationInitializedListener {
  override suspend fun execute() {
    FindSettings.getInstance()
  }
}
