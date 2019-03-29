// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VersionedLazyExtensionPointBean<T> extends AbstractExtensionPointBean {
  @Attribute("implementation")
  public String implementationClass;
  private final NotNullLazyValue<T> myHandler = NotNullLazyValue.createValue(() -> {
    try {
      return instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      throw new PluginException(e, getPluginId());
    }
  });
  @Attribute("version")
  @Nullable
  public String version;

  @NotNull
  public T getHandler() {
    return myHandler.getValue();
  }

  @Nullable
  public String getVersionString() {
    return version;
  }
}
