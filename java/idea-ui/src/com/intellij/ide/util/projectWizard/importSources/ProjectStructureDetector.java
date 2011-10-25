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
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class ProjectStructureDetector {
  public static final ExtensionPointName<ProjectStructureDetector> EP_NAME = ExtensionPointName.create("com.intellij.projectStructureDetector");

  @NotNull
  public abstract DirectoryProcessingResult detectRoots(@NotNull File dir, @NotNull File[] children, @NotNull File base,
                                                        @NotNull List<DetectedProjectRoot> result);

  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor, WizardContext context,
                                                  Icon stepIcon) {
    return Collections.emptyList();
  }

  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots, @NotNull ProjectFromSourcesBuilder builder, @NotNull WizardContext context) {
  }

  public static class DirectoryProcessingResult {
    private boolean myProcessChildren;
    private File myParentToSkip;
    public static final DirectoryProcessingResult PROCESS_CHILDREN = new DirectoryProcessingResult(true, null);
    public static final DirectoryProcessingResult SKIP_CHILDREN = new DirectoryProcessingResult(false, null);

    public static DirectoryProcessingResult skipChildrenAndParentsUpTo(@NotNull File parent) {
      return new DirectoryProcessingResult(false, parent);
    }

    private DirectoryProcessingResult(boolean processChildren, File parentToSkip) {
      myProcessChildren = processChildren;
      myParentToSkip = parentToSkip;
    }

    public boolean isProcessChildren() {
      return myProcessChildren;
    }

    @Nullable
    public File getParentToSkip() {
      return myParentToSkip;
    }
  }
}
