// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ActionManagerListener {
  @Topic.AppLevel
  Topic<ActionManagerListener> TOPIC = Topic.create("Action Manager Listener", ActionManagerListener.class);

  default void toolbarCreated(@NotNull String place, @NotNull ActionGroup group, boolean horizontal, @NotNull ActionToolbar toolbar) {
  }
}
