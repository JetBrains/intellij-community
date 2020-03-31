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

/**
 * @author Vladislav.Soroka
 */
public interface ProgressBuildEvent extends BuildEvent {
  /**
   * Returns the total amount of work that the event operation is in the progress of performing, or -1 if not known.
   *
   * @return The total amount of work, or -1 if not known.
   */
  long getTotal();

  /**
   * Returns the amount of work already performed by the event.
   *
   * @return The amount of performed work
   */
  long getProgress();

  /**
   * Returns the measure used to express the amount of work.
   *
   * @return The measure used to express the amount of work.
   */
  String getUnit();
}
