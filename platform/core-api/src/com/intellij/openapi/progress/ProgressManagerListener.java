// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProgressManagerListener {
  Topic<ProgressManagerListener> TOPIC = new Topic<>(ProgressManagerListener.class, Topic.BroadcastDirection.NONE, true);

  void onTaskRunnableCreated(@NotNull Task task, @NotNull ProgressIndicator indicator, @Nullable Runnable continuation);

  void onTaskFinished(@NotNull Task task, boolean canceled, @Nullable Throwable error);
}
