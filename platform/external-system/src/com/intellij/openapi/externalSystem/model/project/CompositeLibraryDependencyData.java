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
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.CompositeLibraryDependencyId;
import com.intellij.openapi.externalSystem.model.project.id.LibraryDependencyId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for an entity which references two library dependencies.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 5:28 PM
 */
public class CompositeLibraryDependencyData extends AbstractCompositeData<LibraryDependencyData, LibraryOrderEntry> {

  public CompositeLibraryDependencyData(@NotNull LibraryDependencyData libraryDependencyData,
                                        @NotNull LibraryOrderEntry ideLibraryDependency)
  {
    super(libraryDependencyData, ideLibraryDependency);
  }

  @NotNull
  @Override
  public ProjectEntityId getId(@Nullable DataNode<?> dataNode) {
    Library library = getIdeEntity().getLibrary();
    assert library != null;
    return new CompositeLibraryDependencyId(
      new LibraryDependencyId(getOwner(), getExternalEntity().getOwnerModule().getName(), getExternalEntity().getName()),
      new LibraryDependencyId(ProjectSystemId.IDE, getIdeEntity().getOwnerModule().getName(), ExternalSystemUtil.getLibraryName(library))
    );
  }

  @Override
  public String toString() {
    return String.format("composite library dependency - external: %s, ide: %s", getExternalEntity(), getIdeEntity());
  }
}
