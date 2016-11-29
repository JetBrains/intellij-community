/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "masterDetails", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class MasterDetailsStateService implements PersistentStateComponent<MasterDetailsStateService.States>{
  private final SkipDefaultValuesSerializationFilters mySerializationFilter = new SkipDefaultValuesSerializationFilters();
  private final Map<String, ComponentState> myStates = new HashMap<>();

  public static MasterDetailsStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MasterDetailsStateService.class);
  }

  /**
   * @deprecated override {@link MasterDetailsComponent#getComponentStateKey()} and {@link MasterDetailsComponent#getStateService()} instead
   */
  public void register(String key, MasterDetailsComponent component) {
  }

  @Nullable
  public MasterDetailsState getComponentState(@NotNull @NonNls String key, Class<? extends MasterDetailsState> stateClass) {
    ComponentState state = myStates.get(key);
    if (state == null) return null;
    final Element settings = state.mySettings;
    return settings != null ? XmlSerializer.deserialize(settings, stateClass) : null;
  }

  public void setComponentState(@NotNull @NonNls String key, @NotNull MasterDetailsState state) {
    final Element element = XmlSerializer.serialize(state, mySerializationFilter);
    if (element == null) {
      myStates.remove(key);
    }
    else {
      final ComponentState componentState = new ComponentState();
      componentState.myKey = key;
      componentState.mySettings = element;
      myStates.put(key, componentState);
    }
  }

  public States getState() {
    States states = new States();
    states.myStates.addAll(myStates.values());
    Collections.sort(states.getStates(), (o1, o2) -> o1.myKey.compareTo(o2.myKey));
    return states;
  }

  public void loadState(States states) {
    myStates.clear();
    for (ComponentState state : states.getStates()) {
      myStates.put(state.myKey, state);
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
    private List<ComponentState> myStates = new ArrayList<>();

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
