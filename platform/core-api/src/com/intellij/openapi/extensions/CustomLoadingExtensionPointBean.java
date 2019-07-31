// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.diagnostic.PluginException;
import com.intellij.util.ExtensionInstantiator;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

public class CustomLoadingExtensionPointBean extends AbstractExtensionPointBean {
  @Attribute
  public String factoryClass;

  @Attribute
  public String factoryArgument;

  @NotNull
  protected <T> T instantiateExtension(@Nullable String className, @NotNull PicoContainer picoContainer) {
    T instance;
    if (factoryClass == null) {
      instance = ExtensionInstantiator.instantiateWithPicoContainerOnlyIfNeeded(className, picoContainer, myPluginDescriptor);
    }
    else {
      ExtensionFactory factory = instantiateExtension(factoryClass, picoContainer);
      //noinspection unchecked
      instance = (T)factory.createInstance(factoryArgument, className);
    }
    if (instance instanceof PluginAware) {
      ((PluginAware)instance).setPluginDescriptor(myPluginDescriptor);
    }
    return instance;
  }
}
