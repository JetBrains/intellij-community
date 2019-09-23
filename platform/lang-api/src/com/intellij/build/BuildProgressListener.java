/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface BuildProgressListener {

  /**
   * @deprecated do not use it, it's only for compatibility reason
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  Object UNKNOWN_BUILD_ID = new Object();

  /**
   * @param event
   * @deprecated use {@link #onEvent(Object, BuildEvent)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  default void onEvent(@NotNull BuildEvent event) { onEvent(UNKNOWN_BUILD_ID, event);}

  void onEvent(@NotNull Object buildId, @NotNull BuildEvent event);
}
