// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class SimpleSmartExtensionPoint<T> extends SmartExtensionPoint<T,T>{
  public SimpleSmartExtensionPoint() {
    super(new SmartList<>());
  }

  @Override
  @NotNull
  protected final T getExtension(@NotNull final T t) {
    return t;
  }

  public static <T> SimpleSmartExtensionPoint<T> create(ExtensionPointName<T> epName) {
    return new SimpleSmartExtensionPoint<T>() {
      @NotNull
      @Override
      protected ExtensionPoint<T> getExtensionPoint() {
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  public static <T> SimpleSmartExtensionPoint<T> create(ExtensionsArea area, ExtensionPointName<T> epName) {
    return new SimpleSmartExtensionPoint<T>() {
      @NotNull
      @Override
      protected ExtensionPoint<T> getExtensionPoint() {
        return area.getExtensionPoint(epName);
      }
    };
  }
}
