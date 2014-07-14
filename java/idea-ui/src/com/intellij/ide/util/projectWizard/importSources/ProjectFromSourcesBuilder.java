/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.WizardContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public interface ProjectFromSourcesBuilder {
  @NotNull
  Collection<DetectedProjectRoot> getProjectRoots(@NotNull ProjectStructureDetector detector);

  @NotNull
  ProjectDescriptor getProjectDescriptor(@NotNull ProjectStructureDetector detector);

  String getBaseProjectPath();

  @NotNull
  Set<String> getExistingModuleNames();

  @NotNull
  Set<String> getExistingProjectLibraryNames();

  @NotNull
  WizardContext getContext();

  boolean hasRootsFromOtherDetectors(ProjectStructureDetector thisDetector);

  void setupModulesByContentRoots(ProjectDescriptor projectDescriptor, Collection<DetectedProjectRoot> roots);
}
