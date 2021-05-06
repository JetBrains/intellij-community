// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.util.XmlElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExtensionDescriptor {
  public final String implementation;

  public final Os os;
  public final String orderId;
  public final LoadingOrder order;
  public @Nullable XmlElement element;

  public ExtensionDescriptor(@Nullable String implementation,
                             @Nullable Os os,
                             @Nullable String orderId,
                             @NotNull LoadingOrder order,
                             @Nullable XmlElement element) {
    this.implementation = implementation;
    this.os = os;
    this.orderId = orderId;
    this.order = order;
    this.element = element;
  }

  public enum Os {
    mac, linux, windows, unix, freebsd
  }
}
