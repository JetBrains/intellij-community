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
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(
  name="masterDetails",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class MasterDetailsStateService implements PersistentStateComponent<MasterDetailsStateService.States>{
  private final SkipDefaultValuesSerializationFilters mySerializationFilter = new SkipDefaultValuesSerializationFilters();
  private final Map<String, MasterDetailsComponent> myComponents = new HashMap<String, MasterDetailsComponent>();
  private final States myStates = new States();

  public static MasterDetailsStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MasterDetailsStateService.class);
  }

  public void register(@NotNull @NonNls String key, MasterDetailsComponent masterDetailsComponent) {
    myComponents.put(key, masterDetailsComponent);
    for (ComponentState state : myStates.getStates()) {
      if (key.equals(state.myKey)) {
        final Element element = state.mySettings;
        if (element != null) {
          loadComponentState(masterDetailsComponent, element);
        }
      }
    }
  }

  private static void loadComponentState(MasterDetailsComponent masterDetailsComponent, Element element) {
    final MasterDetailsState loadedState = XmlSerializer.deserialize(element, masterDetailsComponent.getState().getClass());
    masterDetailsComponent.loadState(loadedState);
  }

  public States getState() {
    myStates.getStates().clear();
    for (Map.Entry<String, MasterDetailsComponent> entry : myComponents.entrySet()) {
      final Element element = XmlSerializer.serialize(entry.getValue().getState(), mySerializationFilter);
      if (element != null) {
        final ComponentState state = new ComponentState();
        state.myKey = entry.getKey();
        state.mySettings = element;
        myStates.getStates().add(state);
      }
    }
    Collections.sort(myStates.getStates(), new Comparator<ComponentState>() {
      @Override
      public int compare(ComponentState o1, ComponentState o2) {
        return o1.myKey.compareTo(o2.myKey);
      }
    });
    return myStates;
  }

  public void loadState(States states) {
    myStates.setStates(states.getStates());
    for (ComponentState state : myStates.getStates()) {
      final MasterDetailsComponent component = myComponents.get(state.myKey);
      if (component != null) {
        loadComponentState(component, state.mySettings);
      }
    }
  }

  @Tag("state")
  public static class ComponentState {
    @Attribute("key")
    public String myKey;

    @Tag("settings")
    public Element mySettings;
  }

  public static class States {
    private List<ComponentState> myStates = new ArrayList<ComponentState>();

    @Tag("states")
    @AbstractCollection(surroundWithTag = false)
    public List<ComponentState> getStates() {
      return myStates;
    }

    public void setStates(List<ComponentState> states) {
      myStates = states;
    }
  }
}
