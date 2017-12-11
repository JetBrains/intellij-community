/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.openapi.ui;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
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
    final ComponentState componentState = new ComponentState();
    componentState.myKey = key;
    componentState.mySettings = element;
    myStates.put(key, componentState);
  }

  @Override
  public States getState() {
    States states = new States();
    states.myStates.addAll(myStates.values());
    Collections.sort(states.getStates(), Comparator.comparing(o -> o.myKey));
    return states;
  }

  @Override
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

    @XCollection(style = XCollection.Style.v2)
    public List<ComponentState> getStates() {
      return myStates;
    }

    public void setStates(List<ComponentState> states) {
      myStates = states;
    }
  }
}
