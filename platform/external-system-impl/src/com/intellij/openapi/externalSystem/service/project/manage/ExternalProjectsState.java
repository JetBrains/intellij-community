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

import com.intellij.openapi.externalSystem.view.ExternalProjectsViewState;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 10/23/2014
 */
public class ExternalProjectsState {


  private final Map<String, State> myExternalSystemsState = new NullSafeMap<String, State>() {
    @Nullable
    @Override
    protected State create(String key) {
      return new State();
    }
  };

  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag = false, surroundValueWithTag = false, surroundKeyWithTag = false,
    keyAttributeName = "id", entryTagName = "system")
  public Map<String, State> getExternalSystemsState() {
    return myExternalSystemsState;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExternalSystemsState(Map<String, State> externalSystemsState) {
  }


  @Tag("state")
  public static class State {
    private ExternalProjectsViewState projectsViewState = new ExternalProjectsViewState();

    private final Map<String, TaskActivationState> myExternalSystemsTaskActivation = new NullSafeMap<String, TaskActivationState>() {
      @Nullable
      @Override
      protected TaskActivationState create(String key) {
        return new TaskActivationState();
      }

      @Override
      protected Map<String, TaskActivationState> createMap() {
        return new LinkedHashMap<>();
      }
    };

    @Property(surroundWithTag = false)
    @MapAnnotation(keyAttributeName = "path", entryTagName = "task",
      surroundWithTag = false, surroundValueWithTag = false, surroundKeyWithTag = false, sortBeforeSave = false)
    public Map<String, TaskActivationState> getExternalSystemsTaskActivation() {
      return myExternalSystemsTaskActivation;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setExternalSystemsTaskActivation(Map<String, TaskActivationState> externalSystemsTaskActivation) {
    }

    @Property(surroundWithTag = false)
    public ExternalProjectsViewState getProjectsViewState() {
      return projectsViewState;
    }

    public void setProjectsViewState(ExternalProjectsViewState projectsViewState) {
      this.projectsViewState = projectsViewState;
    }
  }

  public static abstract class NullSafeMap<K,V> extends FactoryMap<K,V> {
    @Override
    public V put(K key, V value) {
      if(value == null) return null;
      return super.put(key, value);
    }
  }
}
