// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class WeigherExtensionPoint extends AbstractExtensionPointBean implements KeyedLazyInstance<Weigher> {

  // these must be public for scrambling compatibility
  @Attribute("key")
  @RequiredElement
  public String key;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  @Attribute("id")
  public String id;

  private final NotNullLazyValue<Weigher> myHandler = NotNullLazyValue.lazy(() -> {
    Class<Weigher> tClass = findExtensionClass(implementationClass);
    final Weigher weigher = ReflectionUtil.newInstance(tClass);
    weigher.setDebugName(id);
    return weigher;
  });

  @NotNull
  @Override
  public Weigher getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return key;
  }
}
