// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.l10n.LocalizationUtil
import kotlinx.coroutines.CoroutineScope

private class LanguageBundleListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    LocalizationUtil.setLocalizationInitialized()

    /*
    val langBundle = LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP::class.java) ?: return
    val pluginClassLoader = (langBundle.pluginDescriptor ?: return).pluginClassLoader
    UtilBundle.loadBundleFromPlugin(pluginClassLoader)
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader)
    DateTimeFormatManager.getInstance().resetFormats()
    */
  }
}
