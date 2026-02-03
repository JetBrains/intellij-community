// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PersistentFindUsagesOptions extends FindUsagesOptions {

  public PersistentFindUsagesOptions(@NotNull Project project) {
    super(project);
  }

  public PersistentFindUsagesOptions(@NotNull Project project,
                                     @Nullable DataContext dataContext) {
    super(project, dataContext);
  }

  public PersistentFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);
  }

  public abstract void setDefaults(@NotNull Project project);

  public abstract void storeDefaults(@NotNull Project project);

}
