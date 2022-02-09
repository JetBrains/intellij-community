// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "masterDetails", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class MasterDetailsStateService implements PersistentStateComponent<MasterDetailsStateService.States>{
  private final Map<String, ComponentState> myStates = new HashMap<>();

  public static MasterDetailsStateService getInstance(@NotNull Project project) {
    return project.getService(MasterDetailsStateService.class);
  }

  @Nullable
  public MasterDetailsState getComponentState(@NotNull @NonNls String key, Class<? extends MasterDetailsState> stateClass) {
    ComponentState state = myStates.get(key);
    if (state == null) return null;
    final Element settings = state.mySettings;
    return settings == null ? null : XmlSerializer.deserialize(settings, stateClass);
  }

  public void setComponentState(@NotNull @NonNls String key, @NotNull MasterDetailsState state) {
    final Element element = XmlSerializer.serialize(state);
    final ComponentState componentState = new ComponentState();
    componentState.myKey = key;
    componentState.mySettings = element;
    myStates.put(key, componentState);
  }

  @Override
  public States getState() {
    States states = new States();
    states.myStates.addAll(myStates.values());
    states.getStates().sort(Comparator.comparing(o -> o.myKey));
    return states;
  }

  @Override
  public void loadState(@NotNull States states) {
    myStates.clear();
    for (ComponentState state : states.getStates()) {
      myStates.put(state.myKey, state);
    }
  }

  @Tag("state")
  public static final class ComponentState {
    @Attribute("key")
    public @NonNls String myKey;

    @Tag("settings")
    public Element mySettings;
  }

  public static final class States {
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
