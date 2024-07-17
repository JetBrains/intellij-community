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
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An event that informs about an interim result of the operation.
 *
 * @author Vladislav.Soroka
 */
public class ExternalSystemStatusEvent<T extends OperationDescriptor> extends ExternalSystemProgressEvent<T> {

  private final long myTotal;
  private final long myProgress;
  private final String myUnit;
  @Nullable
  private final String myDescription;

  public ExternalSystemStatusEvent(@NotNull String eventId, @Nullable String parentEventId, @NotNull T descriptor,
                                   long total, long progress, String unit) {
    this(eventId, parentEventId, descriptor, total, progress, unit, null);
  }

  public ExternalSystemStatusEvent(@NotNull String eventId, @Nullable String parentEventId, @NotNull T descriptor,
                                   long total, long progress, String unit, @Nullable String description) {
    super(eventId, parentEventId, descriptor);
    myTotal = total;
    myProgress = progress;
    myUnit = unit;
    myDescription = description;
  }

  /**
   * The amount of work already performed by the build operation.
   *
   * @return The amount of performed work
   */
  public long getProgress() {
    return myProgress;
  }

  /**
   * The total amount of work that the build operation is in the progress of performing, or -1 if not known.
   *
   * @return The total amount of work, or -1 if not known.
   */
  public long getTotal() {
    return myTotal;
  }

  /**
   * The measure used to express the amount of work.
   *
   * @return The measure used to express the amount of work.
   */
  public String getUnit() {
    return myUnit;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }
}
