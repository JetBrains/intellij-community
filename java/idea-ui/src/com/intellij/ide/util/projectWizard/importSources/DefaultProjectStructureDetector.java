// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.openapi.module.WebModuleTypeBase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

public final class DefaultProjectStructureDetector extends ProjectStructureDetector {
  @Override
  public @NotNull DirectoryProcessingResult detectRoots(@NotNull File dir,
                                                        File @NotNull [] children,
                                                        @NotNull File base,
                                                        @NotNull List<DetectedProjectRoot> result) {
    result.add(new DetectedContentRoot(dir, JavaUiBundle.message("default.project.structure.root.type.name"), WebModuleTypeBase.getInstance()));
    return DirectoryProcessingResult.SKIP_CHILDREN;
  }

  @Override
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                    @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
    if (!builder.hasRootsFromOtherDetectors(this)) {
      builder.setupModulesByContentRoots(projectDescriptor, roots);
    }
  }
}
