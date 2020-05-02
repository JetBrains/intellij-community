// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class KeyedLazyInstanceEP<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    @Override
    protected Class<T> getInstanceClass() {
      return findExtensionClass(implementationClass);
    }
  };

  @Override
  public @NotNull T getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return key;
  }
}