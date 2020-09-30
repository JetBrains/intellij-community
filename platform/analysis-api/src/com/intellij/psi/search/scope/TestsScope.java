// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class TestsScope extends NamedScope {
  public static final String NAME = "Tests";
  public static final TestsScope INSTANCE = new TestsScope();

  private TestsScope() {
    super(NAME, () -> AnalysisBundle.message("tests.scope.name"), IconManager.getInstance().createOffsetIcon(AllIcons.Scope.Tests), new FilteredPackageSet(NAME) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        return TestSourcesFilter.isTestSources(file, project);
      }
    });
  }

  @Override
  public String getDefaultColorName() {
    return "Green";
  }
}
