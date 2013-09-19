package com.intellij.vcs.log;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
@State(name = "Vcs.Log.Settings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogSettings implements PersistentStateComponent<VcsLogSettings.State> {

  private State myState = new State();

  public static class State {
    public boolean SHOW_DETAILS = false;
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public boolean isShowDetails() {
    return myState.SHOW_DETAILS;
  }

  public void setShowDetails(boolean showDetails) {
    myState.SHOW_DETAILS = showDetails;
  }

}
