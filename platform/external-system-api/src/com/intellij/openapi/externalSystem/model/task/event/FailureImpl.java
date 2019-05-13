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

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class FailureImpl implements Failure {
  private final String myMessage;
  private final String myDescription;
  private final List<? extends Failure> myCauses;

  public FailureImpl(String message,
                     String description,
                     List<? extends Failure> causes) {
    myMessage = message;
    myDescription = description;
    myCauses = causes;
  }

  @Nullable
  @Override
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public List<? extends Failure> getCauses() {
    return myCauses;
  }
}
