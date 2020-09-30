// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.file;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This listener is notified when some operation performs a massive batch file change, and when this change is completed.
 * <p/>
 * To subscribe to such batch file changes, connect to the Application's {@link com.intellij.util.messages.MessageBus}
 * via the {@link #TOPIC} defined below.
 */
public interface BatchFileChangeListener {
  Topic<BatchFileChangeListener> TOPIC = new Topic<>(BatchFileChangeListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * @param project Project where many file changes are expected to happen
   * @param activityName the name of the activity (a noun phrase) causing this file change
   */
  default void batchChangeStarted(@NotNull Project project, @Nullable @Nls String activityName) {}

  default void batchChangeCompleted(@NotNull Project project) {}
}
