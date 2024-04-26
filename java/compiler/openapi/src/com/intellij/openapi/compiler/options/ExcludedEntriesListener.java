// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler.options;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ExcludedEntriesListener {

  /**
   * Compiler's excluded entries modification notification.
   */
  @Topic.ProjectLevel
  Topic<ExcludedEntriesListener> TOPIC = new Topic<>(ExcludedEntriesListener.class, Topic.BroadcastDirection.NONE);

  default void onEntryAdded(@NotNull ExcludeEntryDescription description) { }

  default void onEntryRemoved(@NotNull ExcludeEntryDescription description) { }
}