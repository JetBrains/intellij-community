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
package com.intellij.openapi.externalSystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 7/14/2014
 */
public class DefaultExternalTask implements ExternalTask {
  private static final long serialVersionUID = 1L;

  @NotNull
  private String myName;
  @NotNull
  private String myQName;
  @Nullable
  private String myDescription;
  @Nullable
  private String myGroup;

  public DefaultExternalTask() {
  }

  public DefaultExternalTask(ExternalTask externalTask) {
    myName = externalTask.getName();
    myQName = externalTask.getQName();
    myDescription = externalTask.getDescription();
    myGroup = externalTask.getGroup();
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getQName() {
    return myQName;
  }

  public void setQName(@NotNull String QName) {
    myQName = QName;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }

  @Nullable
  @Override
  public String getGroup() {
    return myGroup;
  }

  public void setGroup(@Nullable String group) {
    myGroup = group;
  }
}
