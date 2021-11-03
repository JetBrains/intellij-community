// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.KeyedExtensionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Defines a factory to create visual representations for root types that can be queried from OrderEntry.
 * @author anna
 * @see com.intellij.openapi.roots.OrderEntry
 * @see SdkPathEditor
 */
public interface OrderRootTypeUIFactory {
  KeyedExtensionFactory<OrderRootTypeUIFactory, OrderRootType> FACTORY =
    new KeyedExtensionFactory<>(OrderRootTypeUIFactory.class, new ExtensionPointName<>("com.intellij.OrderRootTypeUI"), ApplicationManager.getApplication()) {
      @NotNull
      @Override
      public String getKey(@NotNull final OrderRootType key) {
        return key.name();
      }
    };

  @Nullable
  SdkPathEditor createPathEditor(Sdk sdk);

  Icon getIcon();

  String getNodeText();
}
