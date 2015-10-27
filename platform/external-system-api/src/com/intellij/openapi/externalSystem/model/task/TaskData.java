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
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for external system task information.
 * 
 * @author Denis Zhdanov
 * @since 5/15/13 10:59 AM
 */
public class TaskData extends AbstractExternalEntityData implements ExternalConfigPathAware, Comparable<TaskData> {

  private static final long serialVersionUID = 1L;

  @NotNull private final String myName;
  @Nullable private final String myDescription;
  @NotNull private final String myLinkedExternalProjectPath;
  @Nullable private String myGroup;
  @Nullable private String myType;
  private boolean myInherited;

  public TaskData(@NotNull ProjectSystemId owner, @NotNull String name, @NotNull String path, @Nullable String description) {
    super(owner);
    myName = name;
    myLinkedExternalProjectPath = path;
    myDescription = description;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getLinkedExternalProjectPath() {
    return myLinkedExternalProjectPath;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @Nullable
  public String getGroup() {
    return myGroup;
  }

  public void setGroup(@Nullable String group) {
    myGroup = group;
  }

  @Nullable
  public String getType() {
    return myType;
  }

  public void setType(@Nullable String type) {
    myType = type;
  }

  public boolean isInherited() {
    return myInherited;
  }

  public void setInherited(boolean inherited) {
    myInherited = inherited;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + (myGroup != null ? myGroup.hashCode() : 0);
    result = 31 * result + myLinkedExternalProjectPath.hashCode();
    result = 31 * result + (myInherited ? 1 : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TaskData data = (TaskData)o;

    if (myInherited != data.myInherited) return false;
    if (!myName.equals(data.myName)) return false;
    if (myGroup != null ? !myGroup.equals(data.myGroup) : data.myGroup != null) return false;
    if (!myLinkedExternalProjectPath.equals(data.myLinkedExternalProjectPath)) return false;
    if (myDescription != null ? !myDescription.equals(data.myDescription) : data.myDescription != null) return false;

    return true;
  }

  @Override
  public int compareTo(@NotNull TaskData that) {
    return myName.compareTo(that.getName());
  }

  @Override
  public String toString() {
    return myName;
  }
}
