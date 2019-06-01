// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import org.jetbrains.annotations.NotNull;

public final class ListenerDescriptor {
  public final String listenerClassName;
  public final String topicClassName;

  public ListenerDescriptor(@NotNull String listenerClassName, @NotNull String topicClassName) {
    this.listenerClassName = listenerClassName;
    this.topicClassName = topicClassName;
  }
}
