/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build.events;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface BuildEvent {
  /**
   * Returns an ID that uniquely identifies the event.
   * The events with equal IDs replaces each other.
   */
  @NotNull Object getId();

  /**
   * Returns the parent event id, if any.
   */
  @Nullable Object getParentId();

  /**
   * Returns the time, in milliseconds since the epoch, this event was triggered.
   */
  long getEventTime();

  /**
   * Returns textual representation of the event.
   */
  @Message
  @NotNull String getMessage();

  @Hint
  @Nullable String getHint();

  @Description
  @Nullable String getDescription();
}
