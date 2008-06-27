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

  public void register(String key, MasterDetailsComponent masterDetailsComponent) {
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