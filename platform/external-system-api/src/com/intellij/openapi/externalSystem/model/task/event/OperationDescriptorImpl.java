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

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 12/16/2015
 */
public class OperationDescriptorImpl implements OperationDescriptor {
  private static final long serialVersionUID = 1L;

  private final long myEventTime;
  private final String myDisplayName;
  @Nullable private String myHint;

  public OperationDescriptorImpl(String displayName, long eventTime) {
    myDisplayName = displayName;
    myEventTime = eventTime;
  }


  @Override
  public long getEventTime() {
    return myEventTime;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  @Nullable
  public String getHint() {
    return myHint;
  }

  public void setHint(@Nullable String hint) {
    myHint = hint;
  }
}
