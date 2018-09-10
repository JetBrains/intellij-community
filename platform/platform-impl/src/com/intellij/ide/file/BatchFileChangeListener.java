/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.file;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This listener is notified when some operation performs a massive batch file change, and when this change is completed.
 * <p/>
 * To subscribe to such batch file changes, connect to the Project's {@link com.intellij.util.messages.MessageBus}
 * via the {@link #TOPIC} defined below.
 */
public interface BatchFileChangeListener {

  Topic<BatchFileChangeListener> TOPIC = Topic.create("Batch File Update", BatchFileChangeListener.class);

  /**
   * @param project Project where many file changes are expected to happen
   * @param activityName the name of the activity (a noun phrase) causing this file change
   */
  default void batchChangeStarted(@NotNull Project project, @Nullable String activityName) {}

  default void batchChangeCompleted(@NotNull Project project) {}
}
