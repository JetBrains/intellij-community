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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Common base class for external system settings. Defines a minimal api which is necessary for the common external system
 * support codebase.
 * <p/>
 * <b>Note:</b> non-abstract sub-classes of this class are expected to be marked by {@link State} annotation configured as necessary.
 *  
 * @author Denis Zhdanov
 * @since 4/3/13 4:04 PM
 */
public abstract class AbstractExternalSystemSettings<S extends ExternalProjectSettings, L extends ExternalSystemSettingsListener<S>> {
  
  @NotNull private final Topic<L> myChangesTopic;
  @NotNull private final Project  myProject;

  @NotNull private final Map<String/* project path */, S> myLinkedProjectsSettings = ContainerUtilRt.newHashMap();

  protected AbstractExternalSystemSettings(@NotNull Topic<L> topic, @NotNull Project project) {
    myChangesTopic = topic;
    myProject = project;
  }
  
  @SuppressWarnings("unchecked")
  @NotNull
  public Collection<S> getLinkedProjectsSettings() {
    return myLinkedProjectsSettings.values();
  }

  @Nullable
  public S getLinkedProjectSettings(@NotNull String linkedProjectPath) {
    return myLinkedProjectsSettings.get(linkedProjectPath);
  }
  
  /**
   * Un-links given external project from the current ide project.
   * 
   * @param linkedProjectPath  path of external project to be unlinked
   * @return                   <code>true</code> if there was an external project with the given config path linked to the current
   *                           ide project;
   *                           <code>false</code> otherwise
   */
  public boolean unlinkExternalProject(@NotNull String linkedProjectPath) {
    S removed = myLinkedProjectsSettings.remove(linkedProjectPath);
    if (removed == null) {
      return false;
    }
    
    getPublisher().onProjectsUnlinked(Collections.singleton(linkedProjectPath));
    return true;
  }

  public void setLinkedProjectsSettings(@NotNull Collection<S> settings) {
    List<S> added = ContainerUtilRt.newArrayList();
    Map<String, S> removed = ContainerUtilRt.newHashMap(myLinkedProjectsSettings);
    myLinkedProjectsSettings.clear();
    for (S current : settings) {
      myLinkedProjectsSettings.put(current.getExternalProjectPath(), current);
    }
    
    for (S current : settings) {
      S old = removed.remove(current.getExternalProjectPath());
      if (old == null) {
        added.add(current);
      }
      else {
        if (current.isUseAutoImport() != old.isUseAutoImport()) {
          getPublisher().onUseAutoImportChange(current.isUseAutoImport(), current.getExternalProjectPath());
        }
        checkSettings(old, current);
      }
    }
    if (!added.isEmpty()) {
      getPublisher().onProjectsLinked(added);
    }
    if (!removed.isEmpty()) {
      getPublisher().onProjectsUnlinked(removed.keySet());
    }
  }

  /**
   * Is assumed to check if given old settings external system-specific state differs from the given new one
   * and {@link #getPublisher() notify} listeners in case of the positive answer.
   * 
   * @param old      old settings state
   * @param current  current settings state
   */
  protected abstract void checkSettings(@NotNull S old, @NotNull S current);

  @NotNull
  public Topic<L> getChangesTopic() {
    return myChangesTopic;
  }

  @NotNull
  public L getPublisher() {
    return myProject.getMessageBus().syncPublisher(myChangesTopic);
  }

  protected void fillState(@NotNull State<S> state) {
    state.setLinkedExternalProjectsSettings(ContainerUtilRt.newTreeSet(myLinkedProjectsSettings.values()));
  }

  @SuppressWarnings("unchecked")
  protected void loadState(@NotNull State<S> state) {
    Set<S> settings = state.getLinkedExternalProjectsSettings();
    if (settings != null) {
      myLinkedProjectsSettings.clear();
      for (S s : settings) {
        myLinkedProjectsSettings.put(s.getExternalProjectPath(), s);
      }
    }
  }

  public interface State<S> {
    
    Set<S> getLinkedExternalProjectsSettings();

    void setLinkedExternalProjectsSettings(Set<S> settings);
  }
}
