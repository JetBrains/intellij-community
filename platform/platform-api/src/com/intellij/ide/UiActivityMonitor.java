/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.BusyObject;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class UiActivityMonitor implements ApplicationComponent {

  private Map<Object, BusyImpl> myObjects = new HashMap<Object, BusyImpl>();

  public UiActivityMonitor() {
    myObjects.put(null, new BusyObjectGlobalImpl());
  }

  @Override
  public void initComponent() {
    ProjectManager pm = ProjectManager.getInstance();
    pm.addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectClosed(Project project) {
        myObjects.remove(project);
      }
    });
    initBusyObjectFor(pm.getDefaultProject());
  }

  public BusyObject getBusy(@NotNull Project project) {
    return _getBusy(project);
  }

  public BusyObject getBusy() {
    return _getBusy(null);
  }


  public void addActivity(@NotNull Project project, Object activity) {
    if (!hasObjectFor(project)) {
      Project[] open = ProjectManager.getInstance().getOpenProjects();
      for (Project each : open) {
        if (each == project) {
          initBusyObjectFor(project);
          break;
        }
      }
    }

    _getBusy(project).addActivity(activity);
  }

  boolean hasObjectFor(Project project) {
    return myObjects.containsKey(project);
  }

  public void removeActivity(@NotNull Project project, Object activity) {
    _getBusy(project).removeActivity(activity);
  }

  public void addActivity(Object activity) {
    _getBusy(null).addActivity(activity);
  }

  public void removeActivity(Object activity) {
    _getBusy(null).removeActivity(activity);
  }

  private BusyImpl _getBusy(@Nullable Object key) {
    BusyImpl object = myObjects.get(key);
    return object != null ? object : getGlobalBusy();
  }
  
  void initBusyObjectFor(@Nullable Object key) {
    BusyImpl object = new BusyImpl();
    myObjects.put(key, object);
  }
  
  private BusyObjectGlobalImpl getGlobalBusy() {
    return (BusyObjectGlobalImpl)myObjects.get(null);
  }


  public static UiActivityMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(UiActivityMonitor.class);
  }
  
  @Override
  public void disposeComponent() {
    myObjects.clear();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UiActivityMonitor";
  }

  private class BusyImpl extends BusyObject.Impl {

    private Set<Object> myActivities = new HashSet<Object>();

    @Override
    public boolean isReady() {
      return isOwnReady() && getGlobalBusy().isOwnReady();
    }

    boolean isOwnReady() {
      return myActivities.size() == 0;
    }


    public void addActivity(Object activity) {
      myActivities.add(activity);
    }

    public void removeActivity(Object activity) {
      myActivities.remove(activity);
      onReady();
    }
  }

  private class BusyObjectGlobalImpl extends BusyImpl {

    @Override
    public boolean isReady() {
      Iterator<Object> iterator = myObjects.keySet().iterator();
      while (iterator.hasNext()) {
        Object eachKey = iterator.next();
        BusyImpl busy = myObjects.get(eachKey);
        if (busy == this) continue;
        if (!busy.isOwnReady()) return false;
      }

      return isOwnReady();
    }
  }
}
