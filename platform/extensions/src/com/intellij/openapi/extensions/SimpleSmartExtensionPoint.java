// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
 */
public abstract class SimpleSmartExtensionPoint<T> extends SmartExtensionPoint<T,T>{
  public SimpleSmartExtensionPoint(@NotNull final Collection<T> explicitExtensions) {
    super(explicitExtensions);
  }

  public SimpleSmartExtensionPoint() {
    super(new SmartList<>());
  }

  @Override
  @NotNull
  protected final T getExtension(@NotNull final T t) {
    return t;
  }
}