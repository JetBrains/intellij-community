// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.DynamicBundle.LanguageBundleEP
import com.intellij.UtilBundle
import com.intellij.core.CoreBundle
import com.intellij.ui.UtilUiBundle

private class LanguageBundleListener : ApplicationInitializedListener {
  override fun componentsInitialized() {
    val langBundle = LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP::class.java)
    val pluginDescriptor = langBundle?.pluginDescriptor ?: return
    val pluginClassLoader = pluginDescriptor.pluginClassLoader
    UtilBundle.loadBundleFromPlugin(pluginClassLoader)
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader)
    DynamicBundle.loadLocale(langBundle)
    CoreBundle.clearCache()
  }
}
