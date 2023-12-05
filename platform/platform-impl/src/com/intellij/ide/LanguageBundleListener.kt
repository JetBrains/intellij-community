// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.DynamicBundle.LanguageBundleEP
import com.intellij.UtilBundle
import com.intellij.core.CoreBundle
import com.intellij.ui.UtilUiBundle
import com.intellij.util.text.DateTimeFormatManager
import kotlinx.coroutines.CoroutineScope

private class LanguageBundleListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val langBundle = LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP::class.java) ?: return
    val pluginClassLoader = (langBundle.pluginDescriptor ?: return).pluginClassLoader
    UtilBundle.loadBundleFromPlugin(pluginClassLoader)
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader)
    DynamicBundle.loadLocale(langBundle)
    CoreBundle.clearCache()
    DateTimeFormatManager.getInstance().resetFormats()
  }
}
