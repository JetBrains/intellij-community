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

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/14/2014
 */
public class DefaultExternalSourceSet implements ExternalSourceSet {
  private static final long serialVersionUID = 1L;

  private String myName;
  private Map<IExternalSystemSourceType, ExternalSourceDirectorySet> mySources;

  public DefaultExternalSourceSet() {
    mySources = new HashMap<IExternalSystemSourceType, ExternalSourceDirectorySet>();
  }

  public DefaultExternalSourceSet(ExternalSourceSet sourceSet) {
    this();
    myName = sourceSet.getName();
    for (Map.Entry<IExternalSystemSourceType, ExternalSourceDirectorySet> entry : sourceSet.getSources().entrySet()) {
      mySources.put(ExternalSystemSourceType.from(entry.getKey()), new DefaultExternalSourceDirectorySet(entry.getValue()));
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @NotNull
  @Override
  public Map<IExternalSystemSourceType, ExternalSourceDirectorySet> getSources() {
    return mySources;
  }

  public void setSources(Map<IExternalSystemSourceType, ExternalSourceDirectorySet> sources) {
    mySources = sources;
  }
}
