/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.facet;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.roots.ProjectModelElement;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.FacetManagerState;

/**
 * @author nik
 */
@State(name = "FacetsFromExternalSources")
public class FacetFromExternalSourcesStorage implements PersistentStateComponent<FacetManagerState>, ProjectModelElement {
  private FacetManagerState myState = new FacetManagerState();
  private Module myModule;

  public static FacetFromExternalSourcesStorage getInstance(Module module) {
    return ModuleServiceManager.getService(module, FacetFromExternalSourcesStorage.class);
  }

  public FacetFromExternalSourcesStorage(@NotNull Module module) {
    myModule = module;
  }

  @Override
  @NotNull
  public FacetManagerState getState() {
    myState = ((FacetManagerImpl)FacetManager.getInstance(myModule)).saveState(FacetManagerImpl.getImportedFacetPredicate(myModule.getProject()));
    return myState;
  }

  @NotNull
  FacetManagerState getLoadedState() {
    return myState;
  }

  @Nullable
  @Override
  public ProjectModelExternalSource getExternalSource() {
    //If different facets came from different external sources it actually doesn't matter which source is returned from this method,
    // it's enough to return any non-null value to serialize this component into a separate file.
    return ContainerUtil.getFirstItem(((FacetManagerImpl)FacetManager.getInstance(myModule)).getExternalSources());
  }

  @Override
  public void loadState(FacetManagerState state) {
    XmlSerializerUtil.copyBean(state, myState);
  }
}
