// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class LanguageExtensionPoint<T> extends CustomLoadingExtensionPointBean implements KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  @Attribute("language")
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  private final NotNullLazyValue<T> myHandler = NotNullLazyValue.createValue(() -> instantiateExtension(implementationClass, ApplicationManager.getApplication().getPicoContainer()));

  @NotNull
  @Override
  public T getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return language;
  }
}
