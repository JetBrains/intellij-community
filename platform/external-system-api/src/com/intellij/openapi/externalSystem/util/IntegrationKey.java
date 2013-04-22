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
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Unique key which encapsulates information about target ide project and external project management system.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/10/13 11:39 AM
 */
public class IntegrationKey {

  @NotNull private final String          myIdeProjectName;
  @NotNull private final String          myIdeProjectLocationHash;
  @NotNull private final ProjectSystemId myExternalSystemId;

  public IntegrationKey(@NotNull Project ideProject, @NotNull ProjectSystemId externalSystemId) {
    this(ideProject.getName(), ideProject.getLocationHash(), externalSystemId);
  }

  public IntegrationKey(@NotNull String ideProjectName,
                        @NotNull String ideProjectLocationHash,
                        @NotNull ProjectSystemId externalSystemId)
  {
    myIdeProjectName = ideProjectName;
    myIdeProjectLocationHash = ideProjectLocationHash;
    myExternalSystemId = externalSystemId;
  }

  @NotNull
  public String getIdeProjectName() {
    return myIdeProjectName;
  }

  @NotNull
  public String getIdeProjectLocationHash() {
    return myIdeProjectLocationHash;
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @Override
  public int hashCode() {
    int result = myIdeProjectName.hashCode();
    result = 31 * result + myIdeProjectLocationHash.hashCode();
    result = 31 * result + myExternalSystemId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntegrationKey key = (IntegrationKey)o;

    if (!myExternalSystemId.equals(key.myExternalSystemId)) return false;
    if (!myIdeProjectLocationHash.equals(key.myIdeProjectLocationHash)) return false;
    if (!myIdeProjectName.equals(key.myIdeProjectName)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s'", myExternalSystemId.toString().toLowerCase(), myIdeProjectName);
  }
}
