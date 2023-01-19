// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface IconLayerProvider {
  ExtensionPointName<IconLayerProvider> EP_NAME = new ExtensionPointName<>("com.intellij.iconLayerProvider");

  @Nullable
  Icon getLayerIcon(@NotNull Iconable element, boolean isLocked);

  @NotNull
  String getLayerDescription();
}
