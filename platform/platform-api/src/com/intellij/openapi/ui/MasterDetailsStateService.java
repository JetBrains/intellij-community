/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Jun-2008
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@State(
  name="masterDetails",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class MasterDetailsStateService implements PersistentStateComponent<MasterDetailsStateService.State>{
  private final Map<String, MasterDetailsComponent> myComponents = new HashMap<String, MasterDetailsComponent>();
  private final State myStates = new State();

  public static MasterDetailsStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MasterDetailsStateService.class);
  }

  public void register(@NonNls String key, MasterDetailsComponent masterDetailsComponent) {
    myComponents.put(key, masterDetailsComponent);
    final MasterDetailsComponent.UIState loadedState = myStates.getStates().get(key);
    if (loadedState != null) {
      masterDetailsComponent.loadState(loadedState);
    }
  }

  public State getState() {
    for (Map.Entry<String, MasterDetailsComponent> entry : myComponents.entrySet()) {
      myStates.getStates().put(entry.getKey(), entry.getValue().getState());
    }
    return myStates;
  }

  public void loadState(State state) {
    myStates.setStates(state.getStates());
    for (Map.Entry<String, MasterDetailsComponent.UIState> entry : myStates.getStates().entrySet()) {
      final MasterDetailsComponent component = myComponents.get(entry.getKey());
      if (component != null) {
        component.loadState(entry.getValue());
      }
    }
  }

  public static class State {
    private Map<String, MasterDetailsComponent.UIState> myStates = new HashMap<String, MasterDetailsComponent.UIState>();

    @Tag("states")
    @MapAnnotation(surroundWithTag = false, entryTagName = "state", surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, MasterDetailsComponent.UIState> getStates() {
      return myStates;
    }

    public void setStates(final Map<String, MasterDetailsComponent.UIState> states) {
      myStates = states;
    }
  }
}
