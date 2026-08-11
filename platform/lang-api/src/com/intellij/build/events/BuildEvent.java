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
 * Represents a single event in a build process, reported to the Build tool window.
 *
 * <h3>Creating New Events</h3>
 *
 * <p> For creating new event use the static {@code builder(...)} method on the event interface
 * (e.g., {@link MessageEvent#builder}, {@link ProgressBuildEvent#builder}):
 * <pre>{@code
 * MessageEvent.builder("Compilation failed", MessageEvent.Kind.ERROR)
 *     .withParentId(parentId)
 *     .build();
 * }</pre>
 *
 * <h3>Defining New Events</h3>
 *
 * <ol>
 *   <li>Define a new interface extending {@code BuildEvent} and an immutable implementation
 *       (preferably a Kotlin {@code data class}).
 *   <li>Define a builder interface and implementation following the pattern of existing builders
 *       (e.g., {@link com.intellij.build.eventBuilders.MessageEventBuilder}).
 *   <li>Register a factory method in {@link BuildEvents} and its implementation in {@code BuildEventsImpl}.
 *   <li>Add a static {@code builder(...)} convenience method on the new event interface that delegates to {@link BuildEvents}.
 * </ol>
 *
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
