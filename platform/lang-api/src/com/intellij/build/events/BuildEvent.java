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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface BuildEvent {
  /**
   * Returns an id that uniquely identifies the event.
   *
   * @return The event id.
   */
  @NotNull
  Object getId();

  /**
   * Returns the parent event id, if any.
   *
   * @return The parent event id.
   */
  @Nullable
  Object getParentId();

  /**
   * Returns the time this event was triggered.
   *
   * @return The event time, in milliseconds since the epoch.
   */
  long getEventTime();

  /**
   * Returns textual representation of the event.
   *
   * @return The event text message.
   */
  @NotNull
  @BuildEventsNls.Message
  String getMessage();

  @Nullable
  @BuildEventsNls.Hint
  String getHint();

  @Nullable
  @BuildEventsNls.Description
  String getDescription();
}
