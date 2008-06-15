package com.intellij.codeInspection.ex;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

import java.util.Collection;

@State(
  name = "ProjectInspectionProfilesVisibleTreeState",
  storages = {
    @Storage(
        id="other",
        file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private final InspectionProjectProfileManager myManager;

  public ProjectInspectionProfilesVisibleTreeState(InspectionProjectProfileManager manager) {
    myManager = manager;
  }

  public VisibleTreeStateComponent getState() {
    return new VisibleTreeStateComponent(getProfiles());
  }

  protected Collection<Profile> getProfiles() {
    return myManager.getProfiles();
  }

  public void loadState(final VisibleTreeStateComponent state) {
    state.loadState(getProfiles());
  }
}
