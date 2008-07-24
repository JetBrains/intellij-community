package com.intellij.codeInspection.ex;

import com.intellij.profile.Profile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VisibleTreeStateComponent {
  private final Map<String, VisibleTreeState> myProfileNameToState = new HashMap<String, VisibleTreeState>();
  private static final String PROFILE_STATE = "profile-state";
  private static final String PROFILE_NAME = "profile-name";
  private static final String STATE = "state";


  public VisibleTreeStateComponent() {
  }

  public VisibleTreeStateComponent(final Collection<Profile> profiles) {
    for (Profile profile : profiles) {
      if (profile instanceof InspectionProfileImpl) {
        saveState(((InspectionProfileImpl)profile));
      }
    }
  }

  private void saveState(final InspectionProfileImpl inspectionProfile) {
    myProfileNameToState.put(inspectionProfile.getName(), inspectionProfile.getVisibleTreeState());
  }

  public void loadState(final InspectionProfileImpl inspectionProfile) {
    VisibleTreeState state = myProfileNameToState.get(inspectionProfile.getName());
    if (state != null) {
      inspectionProfile.setVisibleTreeState(state);
    }
  }

  public void loadState(final Collection<Profile> profiles) {
    for (Profile profile : profiles) {
      if (profile instanceof InspectionProfileImpl) {
        loadState(((InspectionProfileImpl)profile));
      }
    }
  }

}
