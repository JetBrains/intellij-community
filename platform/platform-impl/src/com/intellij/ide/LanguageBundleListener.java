// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.ui.UIBundle;

public class LanguageBundleListener implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    DynamicBundle.LanguageBundleEP langBundle = DynamicBundle.LanguageBundleEP.EP_NAME.findExtension(DynamicBundle.LanguageBundleEP.class);
    PluginDescriptor pd = langBundle != null ? langBundle.getPluginDescriptor() : null;
    if (pd == null) return;
    ClassLoader pluginClassLoader = pd.getPluginClassLoader();
    CommonBundle.setBundle(pluginClassLoader);
    UIBundle.setBundle(pluginClassLoader);
  }
}
