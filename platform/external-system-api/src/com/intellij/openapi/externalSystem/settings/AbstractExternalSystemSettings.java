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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
public abstract class AbstractExternalSystemSettings<
  SS extends AbstractExternalSystemSettings<SS, PS, L>,
  PS extends ExternalProjectSettings,
  L extends ExternalSystemSettingsListener<PS>>
  implements Disposable
{

  @NotNull private final Topic<L> myChangesTopic;
  
  private Project  myProject;

  @NotNull private final Map<String/* project path */, PS> myLinkedProjectsSettings = ContainerUtilRt.newHashMap();
  
  @NotNull private final Map<String/* project path */, PS> myLinkedProjectsSettingsView
    = Collections.unmodifiableMap(myLinkedProjectsSettings);

  protected AbstractExternalSystemSettings(@NotNull Topic<L> topic, @NotNull Project project) {
    myChangesTopic = topic;
    myProject = project;
    Disposer.register(project, this);
  }

  @Override
  public void dispose() {
    myProject = null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * Every time particular external system setting is changed corresponding message is sent via ide
   * <a href="https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Messaging+infrastructure">messaging sub-system</a>.
   * The problem is that every external system implementation defines it's own topic/listener pair. Listener interface is derived
   * from the common {@link ExternalSystemSettingsListener} interface and is specific to external sub-system implementation.
   * However, it's possible that a client wants to perform particular actions based only on {@link ExternalSystemSettingsListener}
   * facilities. There is no way for such external system-agnostic client to create external system-specific listener
   * implementation then.
   * <p/>
   * That's why this method allows to wrap given 'generic listener' into external system-specific one.
   *
   * @param listener  target generic listener to wrap to external system-specific implementation
   */
  public abstract void subscribe(@NotNull ExternalSystemSettingsListener<PS> listener);

  public void copyFrom(@NotNull SS settings) {
    for (PS projectSettings : settings.getLinkedProjectsSettings()) {
      myLinkedProjectsSettings.put(projectSettings.getExternalProjectPath(), projectSettings);
    }
    copyExtraSettingsFrom(settings);
  }
  
  protected abstract void copyExtraSettingsFrom(@NotNull SS settings);
  
  @SuppressWarnings("unchecked")
  @NotNull
  public Collection<PS> getLinkedProjectsSettings() {
    return myLinkedProjectsSettingsView.values();
  }

  @Nullable
  public PS getLinkedProjectSettings(@NotNull String linkedProjectPath) {
    PS ps = myLinkedProjectsSettings.get(linkedProjectPath);
    if(ps == null) {
      for (PS ps1 : myLinkedProjectsSettings.values()) {
        for (String modulePath : ps1.getModules()) {
          if(linkedProjectPath.equals(modulePath)) return ps1;
        }
      }
    }
    return ps;
  }

  public void linkProject(@NotNull PS settings) throws IllegalArgumentException {
    PS existing = getLinkedProjectSettings(settings.getExternalProjectPath());
    if (existing != null) {
      throw new IllegalArgumentException(String.format(
        "Can't link external project '%s'. Reason: it's already registered at the current ide project",
        settings.getExternalProjectPath()
      ));
    }
    myLinkedProjectsSettings.put(settings.getExternalProjectPath(), settings);
    getPublisher().onProjectsLinked(Collections.singleton(settings));
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
    PS removed = myLinkedProjectsSettings.remove(linkedProjectPath);
    if (removed == null) {
      return false;
    }
    
    getPublisher().onProjectsUnlinked(Collections.singleton(linkedProjectPath));
    return true;
  }

  public void setLinkedProjectsSettings(@NotNull Collection<PS> settings) {
    List<PS> added = ContainerUtilRt.newArrayList();
    Map<String, PS> removed = ContainerUtilRt.newHashMap(myLinkedProjectsSettings);
    myLinkedProjectsSettings.clear();
    for (PS current : settings) {
      myLinkedProjectsSettings.put(current.getExternalProjectPath(), current);
    }
    
    for (PS current : settings) {
      PS old = removed.remove(current.getExternalProjectPath());
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
  protected abstract void checkSettings(@NotNull PS old, @NotNull PS current);

  @NotNull
  public Topic<L> getChangesTopic() {
    return myChangesTopic;
  }

  @NotNull
  public L getPublisher() {
    return myProject.getMessageBus().syncPublisher(myChangesTopic);
  }

  protected void fillState(@NotNull State<PS> state) {
    state.setLinkedExternalProjectsSettings(ContainerUtilRt.newTreeSet(myLinkedProjectsSettings.values()));
  }

  @SuppressWarnings("unchecked")
  protected void loadState(@NotNull State<PS> state) {
    Set<PS> settings = state.getLinkedExternalProjectsSettings();
    if (settings != null) {
      myLinkedProjectsSettings.clear();
      for (PS projectSettings : settings) {
        myLinkedProjectsSettings.put(projectSettings.getExternalProjectPath(), projectSettings);
      }
    }
  }

  public interface State<S> {
    
    Set<S> getLinkedExternalProjectsSettings();

    void setLinkedExternalProjectsSettings(Set<S> settings);
  }
}
