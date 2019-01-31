// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

public class CustomLoadingExtensionPointBean extends AbstractExtensionPointBean {
  @Attribute
  public String factoryClass;

  @Attribute
  public String factoryArgument;

  @NotNull
  protected Object instantiateExtension(String implementationClass, @NotNull PicoContainer picoContainer) throws ClassNotFoundException {
    if (factoryClass == null) {
      return instantiateWithPicoContainerOnlyIfNeeded(implementationClass, picoContainer);
    }
    else {
      ExtensionFactory factory = instantiate(factoryClass, picoContainer);
      return factory.createInstance(factoryArgument, implementationClass);
    }
  }
}
