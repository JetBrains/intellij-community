// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import com.intellij.UtilBundle;
import com.intellij.core.CoreBundle;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.ui.UtilUiBundle;

final class LanguageBundleListener implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    DynamicBundle.LanguageBundleEP langBundle = DynamicBundle.LanguageBundleEP.EP_NAME.findExtension(DynamicBundle.LanguageBundleEP.class);
    PluginDescriptor pluginDescriptor = langBundle == null ? null : langBundle.pluginDescriptor;
    if (pluginDescriptor == null) {
      return;
    }

    ClassLoader pluginClassLoader = pluginDescriptor.getPluginClassLoader();
    UtilBundle.loadBundleFromPlugin(pluginClassLoader);
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader);

    DynamicBundle.loadLocale(langBundle);
    CoreBundle.clearCache();
  }
}
