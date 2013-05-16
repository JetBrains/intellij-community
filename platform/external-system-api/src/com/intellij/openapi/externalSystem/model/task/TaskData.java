/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Container for external system task information.
 * 
 * @author Denis Zhdanov
 * @since 5/15/13 10:59 AM
 */
public class TaskData extends AbstractExternalEntityData {

  @NotNull private final List<ExternalSystemTaskDescriptor> myTasks = ContainerUtilRt.newArrayList();
  
  @NotNull private final String myLinkedProjectPath;

  public TaskData(@NotNull ProjectSystemId owner, @NotNull String linkedProjectPath) {
    super(owner);
    myLinkedProjectPath = linkedProjectPath;
  }

  @NotNull
  public String getLinkedProjectPath() {
    return myLinkedProjectPath;
  }

  public void addTask(@NotNull ExternalSystemTaskDescriptor task) {
    myTasks.add(task);
  }

  @NotNull
  public List<ExternalSystemTaskDescriptor> getTasks() {
    return myTasks;
  }
}
