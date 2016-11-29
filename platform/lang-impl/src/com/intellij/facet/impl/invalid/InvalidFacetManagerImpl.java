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
package com.intellij.facet.impl.invalid;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
@State(name = InvalidFacetManagerImpl.COMPONENT_NAME, storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class InvalidFacetManagerImpl extends InvalidFacetManager implements PersistentStateComponent<InvalidFacetManagerImpl.InvalidFacetManagerState> {
  public static final String COMPONENT_NAME = "InvalidFacetManager";
  private InvalidFacetManagerState myState = new InvalidFacetManagerState();
  private final Project myProject;

  public InvalidFacetManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public boolean isIgnored(@NotNull InvalidFacet facet) {
    return myState.getIgnoredFacets().contains(FacetPointersManager.constructId(facet));
  }

  @Override
  public InvalidFacetManagerState getState() {
    return myState;
  }

  @Override
  public void loadState(InvalidFacetManagerState state) {
    myState = state;
  }

  @Override
  public void setIgnored(@NotNull InvalidFacet facet, boolean ignored) {
    final String id = FacetPointersManager.constructId(facet);
    if (ignored) {
      myState.getIgnoredFacets().add(id);
    }
    else {
      myState.getIgnoredFacets().remove(id);
    }
  }

  @Override
  public List<InvalidFacet> getInvalidFacets() {
    return ProjectFacetManager.getInstance(myProject).getFacets(InvalidFacetType.TYPE_ID);
  }

  public static class InvalidFacetManagerState {
    private Set<String> myIgnoredFacets = new HashSet<>();

    @Tag("ignored-facets")
    @AbstractCollection(surroundWithTag = false, elementTag = "facet", elementValueAttribute = "id")
    public Set<String> getIgnoredFacets() {
      return myIgnoredFacets;
    }

    public void setIgnoredFacets(Set<String> ignoredFacets) {
      myIgnoredFacets = ignoredFacets;
    }
  }
}
