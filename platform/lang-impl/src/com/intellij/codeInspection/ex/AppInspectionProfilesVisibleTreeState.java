package com.intellij.codeInspection.ex;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;

import java.util.Collection;

@State(
  name = "AppInspectionProfilesVisibleTreeState",
  storages = {
    @Storage(
        id="other",
        file = "$OPTIONS$/other.xml"
    )}
)
public class AppInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private final InspectionProfileManager myManager;

  public AppInspectionProfilesVisibleTreeState(InspectionProfileManager manager) {
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
