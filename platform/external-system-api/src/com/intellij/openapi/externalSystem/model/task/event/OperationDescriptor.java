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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class OperationDescriptor implements Serializable {

  private final long myEventTime;
  private final @Nls String myDisplayName;
  private @Nullable @Nls String myHint;

  public OperationDescriptor(@Nls String displayName, long eventTime) {
    myDisplayName = displayName;
    myEventTime = eventTime;
  }

  public long getEventTime() {
    return myEventTime;
  }

  public @Nls String getDisplayName() {
    return myDisplayName;
  }

  public @Nullable @Nls String getHint() {
    return myHint;
  }

  public void setHint(@Nullable @Nls String hint) {
    myHint = hint;
  }
}
