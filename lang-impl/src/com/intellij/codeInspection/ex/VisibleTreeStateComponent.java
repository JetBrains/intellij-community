package com.intellij.codeInspection.ex;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.Profile;
import org.jdom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class VisibleTreeStateComponent implements JDOMExternalizable {
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

  public void readExternal(final Element element) throws InvalidDataException {
    List states = element.getChildren(PROFILE_STATE);
    for (Object state : states) {
      if (state instanceof Element) {
        String profileName = ((Element)state).getAttributeValue(PROFILE_NAME);
        Element stateElement = ((Element)state).getChild(STATE);
        if (profileName != null) {
          VisibleTreeState treeState = new VisibleTreeState();
          treeState.readExternal(stateElement);
          myProfileNameToState.put(profileName, treeState);
        }
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    for (String profileName : myProfileNameToState.keySet()) {
      Element prState = new Element(PROFILE_STATE);
      prState.setAttribute(PROFILE_NAME,  profileName);
      Element stateElement = new Element(STATE);
      myProfileNameToState.get(profileName).writeExternal(stateElement);
      prState.addContent(stateElement);
      element.addContent(prState);
    }
  }
}
