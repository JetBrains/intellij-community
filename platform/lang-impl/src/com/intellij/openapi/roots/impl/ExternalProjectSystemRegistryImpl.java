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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class ExternalProjectSystemRegistryImpl implements ExternalProjectSystemRegistry {
  private final ConcurrentMap<String, ProjectModelExternalSource> myExternalSources = new ConcurrentHashMap<>();

  @Override
  public ProjectModelExternalSource getExternalSource(Module module) {
    //todo[nik] probably it would be better to introduce a special extension point instead
    String externalSystemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);
    if (externalSystemId != null) {
      return getSourceById(externalSystemId);
    }
    if ("true".equals(module.getOptionValue(IS_MAVEN_MODULE_KEY))) {
      return getSourceById(MAVEN_EXTERNAL_SOURCE_ID);
    }
    return null;
  }

  @Override
  @NotNull
  public ProjectModelExternalSource getSourceById(String id) {
    return myExternalSources.computeIfAbsent(id, ProjectModelExternalSourceImpl::new);
  }

  private static class ProjectModelExternalSourceImpl implements ProjectModelExternalSource {
    private final String myId;
    private final String myDisplayName;

    public ProjectModelExternalSourceImpl(String id) {
      myId = id;
      //todo[nik] specify display name explicitly instead, the current code is copied from ProjectSystemId constructor
      myDisplayName = StringUtil.capitalize(myId.toLowerCase(Locale.US));
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myDisplayName;
    }

    @NotNull
    @Override
    public String getId() {
      return myId;
    }
  }
}
