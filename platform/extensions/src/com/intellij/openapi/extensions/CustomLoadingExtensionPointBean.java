// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.ReflectionUtil;
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
    if (factoryClass != null) {
      ExtensionFactory factory = instantiate(factoryClass, picoContainer);
      return factory.createInstance(factoryArgument, implementationClass);
    }
    else {
      if (implementationClass == null) {
        throw new RuntimeException("implementation class is not specified for unknown language extension point, " +
                                   "plugin id: " +
                                   (myPluginDescriptor == null ? "<not available>" : myPluginDescriptor.getPluginId()) + ". " +
                                   "Check if 'implementationClass' attribute is specified");
      }
      Class<Object> clazz = findClass(implementationClass);
      try {
        return ReflectionUtil.newInstance(clazz);
      }
      catch (RuntimeException e) {
        if (e.getCause() instanceof NoSuchMethodException) {
          LOG.error("Bean extension class constructor must not have parameters: " + implementationClass);
          return instantiate(clazz, picoContainer, true);
        }
        else {
          throw e;
        }
      }
    }
  }
}
