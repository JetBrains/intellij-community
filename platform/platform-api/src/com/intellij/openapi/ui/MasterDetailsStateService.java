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
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NonNls;

@State(
  name="masterDetails",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class MasterDetailsStateService implements PersistentStateComponent<MasterDetailsStateService.State>{
  public State myStates = new State();

  public void register(@NonNls String key, MasterDetailsComponent masterDetailsComponent) {
    final MasterDetailsComponent.UIState loadedState = myStates.getStates().get(key);
    if (loadedState != null) masterDetailsComponent.loadState(loadedState);
    myStates.getStates().put(key, masterDetailsComponent.getState());
  }

  public State getState() {
    return myStates;
  }

  public void loadState(State state) {
    myStates.setStates(state.getStates());
  }

  public static class State {
    public Map<String, MasterDetailsComponent.UIState> myStates = new HashMap<String, MasterDetailsComponent.UIState>();

    public Map<String, MasterDetailsComponent.UIState> getStates() {
      return myStates;
    }

    public void setStates(final Map<String, MasterDetailsComponent.UIState> states) {
      myStates = states;
    }
  }
}