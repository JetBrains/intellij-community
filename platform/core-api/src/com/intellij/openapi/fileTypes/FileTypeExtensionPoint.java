// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class FileTypeExtensionPoint<T> extends BaseKeyedLazyInstance<T> implements KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  @Attribute("filetype")
  @RequiredElement
  public String filetype;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  @SuppressWarnings("unused")
  FileTypeExtensionPoint() {
  }

  @TestOnly
  public FileTypeExtensionPoint(@NotNull String filetype, @NotNull T instance) {
    super(instance);

    this.filetype = filetype;
    implementationClass = instance.getClass().getName();
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull String getKey() {
    return filetype;
  }
}