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
 * @author Vladislav.Soroka
 */
public class ExternalSystemStatusEventImpl<T extends OperationDescriptor> extends BaseExternalSystemProgressEvent<T>
  implements ExternalSystemStatusEvent<T> {
  private static final long serialVersionUID = 1L;

  private final long myTotal;
  private final long myProgress;
  private final String myUnit;
  private final String myDescription;

  public ExternalSystemStatusEventImpl(@NotNull String eventId, @Nullable String parentEventId, @NotNull T descriptor,
                                       long total, long progress, String unit) {
    this(eventId, parentEventId, descriptor, total, progress, unit, null);
  }

  public ExternalSystemStatusEventImpl(@NotNull String eventId, @Nullable String parentEventId, @NotNull T descriptor,
                                       long total, long progress, String unit, @Nullable  String description) {
    super(eventId, parentEventId, descriptor);
    myTotal = total;
    myProgress = progress;
    myUnit = unit;
    myDescription = description;
  }

  @Override
  public long getProgress() {
    return myProgress;
  }

  @Override
  public long getTotal() {
    return myTotal;
  }

  @Override
  public String getUnit() {
    return myUnit;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }
}
