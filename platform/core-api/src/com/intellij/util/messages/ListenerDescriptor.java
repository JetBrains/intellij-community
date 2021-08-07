// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ListenerDescriptor {
  public final ExtensionDescriptor.Os os;
  public final String listenerClassName;
  public final String topicClassName;

  public final boolean activeInTestMode;
  public final boolean activeInHeadlessMode;

  public transient PluginDescriptor pluginDescriptor;

  public ListenerDescriptor(@Nullable ExtensionDescriptor.Os os,
                            @NotNull String listenerClassName,
                            @NotNull String topicClassName,
                            boolean activeInTestMode,
                            boolean activeInHeadlessMode) {
    this.os = os;
    this.listenerClassName = listenerClassName;
    this.topicClassName = topicClassName;
    this.activeInTestMode = activeInTestMode;
    this.activeInHeadlessMode = activeInHeadlessMode;
  }
}
