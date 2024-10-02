// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service(Service.Level.PROJECT)
@State(name = "RemoteMappingsManager", storages = @Storage("remote-mappings.xml"))
public final class RemoteMappingsManager implements PersistentStateComponent<RemoteMappingsManager.State> {
  private final State myState = new State();

  public static RemoteMappingsManager getInstance(final @NotNull Project project) {
    return project.getService(RemoteMappingsManager.class);
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  @Tag("state")
  public static class State {
    @SuppressWarnings("FieldMayBeFinal")
    @Tag("list")
    private List<Mappings> myList = new ArrayList<>();

    public List<Mappings> getList() {
      return myList;
    }
  }

  public void setForServer(final @NotNull Mappings mappings) {
    final List<Mappings> list = myState.getList();
    final Iterator<Mappings> iterator = list.iterator();
    while (iterator.hasNext()) {
      final Mappings current = iterator.next();
      if (mappings.getServerId().equals(current.getServerId())) {
        iterator.remove();
      }
    }
    list.add(mappings);
  }

  public @Nullable Mappings getForServer(final @NotNull String prefix, final @NotNull String serverId) {
    final String compoundId = combineWithPrefix(prefix, serverId);
    final List<Mappings> list = myState.getList();
    for (Mappings mappings : list) {
      if (compoundId.equals(mappings.getServerId())) return mappings;
    }
    return null;
  }

  public Mappings create(final @NotNull String prefix, @NotNull String serverId, @NotNull List<PathMappingSettings.PathMapping> settings) {
    final Mappings mappings = new Mappings();
    mappings.setServerId(prefix, serverId);
    mappings.setSettings(settings);
    return mappings;
  }

  @Tag("remote-mappings")
  public static class Mappings {
    private String myServerId;
    // only user-defined kept here
    private List<PathMappingSettings.PathMapping> mySettings = new ArrayList<>();
    // all disabled mappings of any type kept here
    private Map<String, String> myDisabled = new HashMap<>();

    public Mappings() {
    }

    @Attribute("server-id")
    public String getServerId() {
      return myServerId;
    }

    @Tag("settings")
    public List<PathMappingSettings.PathMapping> getSettings() {
      return mySettings;
    }

    @Tag("disabled")
    public Map<String, String> getDisabled() {
      return myDisabled;
    }

    public void setSettings(List<PathMappingSettings.PathMapping> settings) {
      mySettings = settings;
    }

    public void setServerId(String serverId) {
      myServerId = serverId;
    }

    public void setServerId(String prefix, String serverId) {
      myServerId = combineWithPrefix(prefix, serverId);
    }

    public void setDisabled(Map<String, String> disabled) {
      myDisabled = disabled;
    }
  }

  private static @NotNull String combineWithPrefix(String prefix, String serverId) {
    return prefix + "@" + serverId;
  }
}
