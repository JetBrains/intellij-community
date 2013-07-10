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
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.openapi.externalSystem.model.task.TaskData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents {@link TaskData} at the ide side. Is required purely for IJ serialization because {@link TaskData} has only final
 * fields which are initialized at constructor and ide serialization is not capable to handle such scenario properly.
 * 
 * @author Denis Zhdanov
 * @since 5/18/13 11:28 PM
 */
public class ExternalTaskPojo implements Comparable<ExternalTaskPojo> {
  
  @NotNull private String myName;
  @NotNull private String myLinkedExternalProjectPath;
  
  @Nullable private String myDescription;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalTaskPojo() {
    // Required for IJ serialization.
    this("___DUMMY___", "___DUMMY___", null);
  }

  public ExternalTaskPojo(@NotNull String name, @NotNull String linkedExternalProjectPath, @Nullable String description) {
    myName = name;
    myLinkedExternalProjectPath = linkedExternalProjectPath;
    myDescription = description;
  }

  @NotNull
  public static ExternalTaskPojo from(@NotNull TaskData data) {
    return new ExternalTaskPojo(data.getName(), data.getLinkedExternalProjectPath(), data.getDescription());
  }
  
  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }

  @NotNull
  public String getLinkedExternalProjectPath() {
    return myLinkedExternalProjectPath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setLinkedExternalProjectPath(@NotNull String linkedExternalProjectPath) {
    // Required for IJ serialization.
    myLinkedExternalProjectPath = linkedExternalProjectPath;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myLinkedExternalProjectPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalTaskPojo that = (ExternalTaskPojo)o;

    if (!myLinkedExternalProjectPath.equals(that.myLinkedExternalProjectPath)) return false;
    if (!myName.equals(that.myName)) return false;
    
    return true;
  }

  @Override
  public int compareTo(@NotNull ExternalTaskPojo that) {
    return myName.compareTo(that.getName());
  }

  @Override
  public String toString() {
    return myName;
  }
}
