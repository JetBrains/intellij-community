// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.Colored;
import com.intellij.ui.OffsetIcon;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
@Colored(color = "e7fadb", darkVariant = "2A3B2C")
public final class TestsScope extends NamedScope {
  public static final String NAME = IdeBundle.message("predefined.scope.tests.name");
  public static final TestsScope INSTANCE = new TestsScope();

  private TestsScope() {
    super(NAME, new OffsetIcon(AllIcons.Scope.Tests), new FilteredPackageSet(NAME) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        return TestSourcesFilter.isTestSources(file, project);
      }
    });
  }
}
