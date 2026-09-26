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

import com.intellij.build.eventBuilders.FinishEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface FinishEvent extends BuildEvent {

  @NotNull EventResult getResult();

  @CheckReturnValue
  static @NotNull FinishEventBuilder builder(
    @NotNull Object startId,
    @NotNull @Message String message,
    @NotNull EventResult result
  ) {
    return BuildEvents.getInstance().finish(startId, message, result);
  }
}
