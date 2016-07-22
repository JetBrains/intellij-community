/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author Vladislav.Soroka
* @since 10/30/2014
*/
@Tag("activation")
public class TaskActivationState {
  @Tag("before_sync")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> beforeSyncTasks = new ArrayList<>();

  @Tag("after_sync")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> afterSyncTasks = new ArrayList<>();

  @Tag("before_compile")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> beforeCompileTasks = new ArrayList<>();

  @Tag("after_compile")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> afterCompileTasks = new ArrayList<>();

  @Tag("after_rebuild")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> afterRebuildTask = new ArrayList<>();

  @Tag("before_rebuild")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public List<String> beforeRebuildTask = new ArrayList<>();

  public boolean isEmpty() {
    for (ExternalSystemTaskActivator.Phase phase : ExternalSystemTaskActivator.Phase.values()) {
      if (!getTasks(phase).isEmpty()) return false;
    }
    return true;
  }

  @NotNull
  public List<String> getTasks(@NotNull ExternalSystemTaskActivator.Phase phase) {
    switch (phase) {
      case AFTER_COMPILE:
        return afterCompileTasks;
      case BEFORE_COMPILE:
        return beforeCompileTasks;
      case AFTER_SYNC:
        return afterSyncTasks;
      case BEFORE_SYNC:
        return beforeSyncTasks;
      case AFTER_REBUILD:
        return afterRebuildTask;
      case BEFORE_REBUILD:
        return beforeRebuildTask;
      default:
        throw new IllegalArgumentException("Unknown task activation phase: " + phase);
    }
  }
}
