// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.util.KeyedExtensionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FileTypeExtensionFactory<T> extends KeyedExtensionFactory<T, FileType> {
  public FileTypeExtensionFactory(@NotNull Class<T> interfaceClass, @NonNls @NotNull ExtensionPointName<KeyedFactoryEPBean> epName) {
    super(interfaceClass, epName, ApplicationManager.getApplication());
  }

  @NotNull
  @Override
  public String getKey(@NotNull FileType key) {
    return key.getName();
  }
}