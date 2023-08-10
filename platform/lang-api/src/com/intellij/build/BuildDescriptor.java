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
package com.intellij.build;

import com.intellij.build.events.BuildEventsNls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface BuildDescriptor {
  @NotNull
  Object getId();

  /**
   * The existence of a group id signalizes that
   * this descriptor is associated with some other
   * builds which have the same groupId.
   */
  @Nullable
  default Object getGroupId() {
    return null;
  }

  @NotNull
  @BuildEventsNls.Title
  String getTitle();

  @NotNull
  @NonNls
  String getWorkingDir();

  long getStartTime();
}
