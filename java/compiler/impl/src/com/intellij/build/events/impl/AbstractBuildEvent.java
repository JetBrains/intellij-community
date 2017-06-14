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
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractBuildEvent implements BuildEvent {

  @NotNull
  private final Object myEventId;
  @Nullable
  private final Object myParentId;
  private final long myEventTime;
  @NotNull
  private final String myMessage;
  @Nullable
  private String myDescription;

  public AbstractBuildEvent(@NotNull Object eventId, @Nullable Object parentId, long eventTime, @NotNull String message) {
    myEventId = eventId;
    myParentId = parentId;
    myEventTime = eventTime;
    myMessage = message;
  }

  @Override
  public Object getId() {
    return myEventId;
  }

  @Nullable
  @Override
  public Object getParentId() {
    return myParentId;
  }

  @Override
  public long getEventTime() {
    return myEventTime;
  }

  @NotNull
  @Override
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }
}
