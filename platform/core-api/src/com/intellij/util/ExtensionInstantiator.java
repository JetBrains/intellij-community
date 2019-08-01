// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

public final class ExtensionInstantiator {
  private static final Logger LOG = Logger.getInstance(ExtensionInstantiator.class);

  private ExtensionInstantiator() {
  }

  @NotNull
  public static <T> T instantiateWithPicoContainerOnlyIfNeeded(@Nullable String className,
                                                               @NotNull PicoContainer picoContainer,
                                                               @Nullable PluginDescriptor pluginDescriptor) {
    if (className == null) {
      throw new ExtensionInstantiationException("implementation class is not specified", pluginDescriptor);
    }

    Class<T> clazz;
    try {
      clazz = AbstractExtensionPointBean.findClass(className, pluginDescriptor);
    }
    catch (ClassNotFoundException e) {
      throw new ExtensionInstantiationException(e, pluginDescriptor);
    }

    try {
      return ReflectionUtil.newInstance(clazz, false);
    }
    catch (ProcessCanceledException | ExtensionNotApplicableException e) {
      throw e;
    }
    catch (Throwable e) {
      if (e.getCause() instanceof NoSuchMethodException) {
        Exception exception = new ExtensionInstantiationException("Bean extension class constructor must not have parameters: " + className, pluginDescriptor);
        Application app = ApplicationManager.getApplication();
        if (app != null && app.isUnitTestMode()) {
          LOG.error(exception);
        }
        else {
          LOG.warn(exception);
        }
      }
      else {
        throw new ExtensionInstantiationException(e, pluginDescriptor);
      }
    }

    try {
      return AbstractExtensionPointBean.instantiate(clazz, picoContainer, true);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new ExtensionInstantiationException(e, pluginDescriptor);
    }
  }
}
