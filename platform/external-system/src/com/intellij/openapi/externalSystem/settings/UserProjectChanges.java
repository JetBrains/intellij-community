/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.project.change.user.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds {@link UserProjectChange} per-project.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/8/13 4:52 PM
 */
public class UserProjectChanges implements PersistentStateComponent<UserProjectChanges> {

  private final AtomicReference<Set<UserProjectChange<?>>> myUserChanges
    = new AtomicReference<Set<UserProjectChange<?>>>(new HashSet<UserProjectChange<?>>());

  @NotNull
  public static UserProjectChanges getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, UserProjectChanges.class);
  }
  
  @Nullable
  @Override
  public UserProjectChanges getState() {
    return this;
  }

  @Override
  public void loadState(UserProjectChanges state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @AbstractCollection(
    surroundWithTag = false,
    elementTypes = {
      AddModuleUserChange.class, RemoveModuleUserChange.class,
      AddModuleDependencyUserChange.class, RemoveModuleDependencyUserChange.class,
      AddLibraryDependencyUserChange.class, RemoveLibraryDependencyUserChange.class,
      ModuleDependencyExportedChange.class, ModuleDependencyScopeUserChange.class,
      LibraryDependencyExportedChange.class, LibraryDependencyScopeUserChange.class
    }
  )
  @NotNull
  public Set<UserProjectChange<?>> getUserProjectChanges() {
    return myUserChanges.get();
  }

  public void setUserProjectChanges(@Nullable Set<UserProjectChange<?>> changes) {
    myUserChanges.set(changes);
  }
}
