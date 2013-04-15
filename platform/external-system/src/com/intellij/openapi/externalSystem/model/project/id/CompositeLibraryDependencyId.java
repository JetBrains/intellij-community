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
package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.CompositeLibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 1/22/13 5:26 PM
 */
public class CompositeLibraryDependencyId extends AbstractCompositeExternalEntityId<LibraryDependencyId> {

  public CompositeLibraryDependencyId(@NotNull LibraryDependencyId libraryDependencyId,
                                      @NotNull LibraryDependencyId ideLibraryDependencyId)
  {
    super(ProjectEntityType.DEPENDENCY_TO_OUTDATED_LIBRARY, ProjectSystemId.IDE, ideLibraryDependencyId, libraryDependencyId);
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    ProjectStructureHelper helper = services.getProjectStructureHelper();
    DataNode<LibraryDependencyData> externalDataNode
      = helper.findExternalLibraryDependency(getCounterPartyId(), getOwner(), ideProject);
    if (externalDataNode == null) {
      return null;
    }

    LibraryOrderEntry ideLibraryDependency = helper.findIdeLibraryDependency(getBaseId(), ideProject);
    if (ideLibraryDependency == null) {
      return null;
    }

    return new CompositeLibraryDependencyData(externalDataNode.getData(), ideLibraryDependency);
  }
}
