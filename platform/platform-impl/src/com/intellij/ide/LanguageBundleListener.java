// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import com.intellij.UtilBundle;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.ui.UtilUiBundle;

public class LanguageBundleListener implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    DynamicBundle.LanguageBundleEP langBundle = DynamicBundle.LanguageBundleEP.EP_NAME.findExtension(DynamicBundle.LanguageBundleEP.class);
    PluginDescriptor pd = langBundle != null ? langBundle.getPluginDescriptor() : null;
    if (pd == null) return;
    ClassLoader pluginClassLoader = pd.getPluginClassLoader();
    UtilBundle.loadBundleFromPlugin(pluginClassLoader);
    UtilUiBundle.loadBundleFromPlugin(pluginClassLoader);
  }
}
