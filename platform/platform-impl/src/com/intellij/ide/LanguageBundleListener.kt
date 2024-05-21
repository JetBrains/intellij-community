// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.UtilBundle
import com.intellij.ui.UtilUiBundle
import com.intellij.util.l10n.LanguageBundleEP
import com.intellij.util.l10n.LocalizationUtil
import com.intellij.util.text.DateTimeFormatManager
import kotlinx.coroutines.CoroutineScope

private class LanguageBundleListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val langBundle = LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP::class.java) ?: return
    val pluginClassLoader = (langBundle.pluginDescriptor ?: return).pluginClassLoader
    UtilBundle.loadBundleFromPlugin(pluginClassLoader)
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader)
    LocalizationUtil.isL10nPluginInitialized = true
    DateTimeFormatManager.getInstance().resetFormats()
  }
}
