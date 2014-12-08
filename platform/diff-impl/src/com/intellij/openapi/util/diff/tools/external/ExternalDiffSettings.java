package com.intellij.openapi.util.diff.tools.external;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;


@State(
  name = "ExternalDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class ExternalDiffSettings implements PersistentStateComponent<ExternalDiffSettings.State> {
  private State myState = new State();

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public static ExternalDiffSettings getInstance() {
    return ServiceManager.getService(ExternalDiffSettings.class);
  }

  public static class State {
    public boolean ENABLED = false;
    public boolean DEFAULT = false;
    @NotNull public String EXE_PATH = "";
    @NotNull public String PARAMETERS = "";
  }

  public boolean isEnabled() {
    return myState.ENABLED;
  }

  public void setEnabled(boolean value) {
    myState.ENABLED = value;
  }

  public boolean isDefault() {
    return myState.DEFAULT;
  }

  public void setDefault(boolean value) {
    myState.DEFAULT = value;
  }

  @NotNull
  public String getExePath() {
    return myState.EXE_PATH;
  }

  public void setExePath(@NotNull String path) {
    myState.EXE_PATH = path;
  }

  @NotNull
  public String getParameters() {
    return myState.PARAMETERS;
  }

  public void setParameters(@NotNull String path) {
    myState.PARAMETERS = path;
  }
}
