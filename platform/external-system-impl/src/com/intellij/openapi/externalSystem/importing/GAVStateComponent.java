// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Experimental
@State(name = "ExternalSystem.GAVStateComponent", storages = @Storage("jps2ext.xml"))
public class GAVStateComponent implements PersistentStateComponent<GAVStateComponent.State> {
  public static class State {
    public Set<String> moduleMapping = new HashSet<>();
  }

  private State myState = new State();

  @Override
  public @Nullable GAVStateComponent.State getState() {
    return myState;
  }

  public Map<String, ProjectId> getMapping() {
    Map<String, ProjectId> result = new HashMap<>();
    for (String mappingEncoded: getState().moduleMapping) {
      String[] gavAsArray = mappingEncoded.split(":");
      String module = gavAsArray[0];
      ProjectId projectId = new ProjectId(gavAsArray[1], gavAsArray[2], gavAsArray[3]);
      if (Objects.nonNull(module)) {
        result.put(module, projectId);
      }
    }
    return result;
  }
  
  public void addMapping(String moduleName, String groupId, String artifactId, String version) {
    myState.moduleMapping.add(String.join(":", moduleName, groupId, artifactId, version));
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }
}
