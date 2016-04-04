/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/27/2015
 */
public class ExternalSystemTaskExecutionEvent extends ExternalSystemTaskNotificationEvent {
  private static final long serialVersionUID = 1L;

  @NotNull private final ExternalSystemProgressEvent myProgressEvent;

  public ExternalSystemTaskExecutionEvent(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemProgressEvent progressEvent) {
    super(id, progressEvent.getDisplayName());
    myProgressEvent = progressEvent;
  }

  @NotNull
  public ExternalSystemProgressEvent getProgressEvent() {
    return myProgressEvent;
  }
}
