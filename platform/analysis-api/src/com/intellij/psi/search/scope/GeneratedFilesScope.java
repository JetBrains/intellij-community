// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

public final class GeneratedFilesScope extends NamedScope {

  private static final String ID = "Generated Files";

  public static final GeneratedFilesScope INSTANCE = new GeneratedFilesScope();

  public GeneratedFilesScope() {
    super(ID, AnalysisBundle.messagePointer("generated.files.scope.name"), AllIcons.Modules.GeneratedFolder,
          new FilteredPackageSet(ID) {
            @Override
            public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
              return GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
            }
          }
    );
  }

  @Override
  public @NotNull String getDefaultColorName() {
    return "Gray";
  }
}
