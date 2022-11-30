// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Represents a localized message to be displayed in options UI. Can be either simple message or split-string (see {@link #splitLabel()}).
 * 
 * @param bundle bundle to use
 * @param messageKey message key
 */
public record BundleMessage(@NotNull ResourceBundle bundle, @NotNull String messageKey) implements LocMessage {
  @Override
  public @NotNull @Nls String label() {
    return AbstractBundle.message(bundle, messageKey);
  }
  
  public static @NotNull ResourceBundle getBundle(@NotNull PluginDescriptor descriptor, @Nullable String bundleId) {
    String bundle = Objects.requireNonNullElse(bundleId, descriptor.getResourceBundleBaseName());
    return DynamicBundle.getResourceBundle(descriptor.getClassLoader(), bundle);
  }
}
