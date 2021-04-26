// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExtensionDescriptor {
  public final Os os;
  public final String orderId;
  public final String order;
  public final Element element;

  public ExtensionDescriptor(@Nullable Os os,
                             @Nullable String orderId,
                             @Nullable String order,
                             @Nullable Element element) {
    this.os = os;
    this.orderId = orderId;
    this.order = order;
    this.element = element;
  }

  public enum Os {
    mac, linux, windows, unix, freebsd
  }
}
