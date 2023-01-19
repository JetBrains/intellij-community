// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleSmartExtensionPoint<T> extends SmartExtensionPoint<T,T>{
  public SimpleSmartExtensionPoint() {
    super(new SmartList<>());
  }

  @Override
  protected final @NotNull T getExtension(final @NotNull T t) {
    return t;
  }

  public static <T> SimpleSmartExtensionPoint<T> create(ExtensionPointName<T> epName) {
    return new SimpleSmartExtensionPoint<T>() {
      @Override
      protected @NotNull ExtensionPoint<@NotNull T> getExtensionPoint() {
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  public static <T> SimpleSmartExtensionPoint<T> create(ExtensionsArea area, ExtensionPointName<T> epName) {
    return new SimpleSmartExtensionPoint<T>() {
      @Override
      protected @NotNull ExtensionPoint<@NotNull T> getExtensionPoint() {
        return area.getExtensionPoint(epName);
      }
    };
  }
}
