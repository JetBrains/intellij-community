// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScratchesNamedScope extends NamedScope {
  public static @NotNull @Nls String scratchesAndConsoles() { return IdeBundle.message("scratches.and.consoles"); }
  public static final @NonNls String ID = "Scratches and Consoles";

  public ScratchesNamedScope() {
    super(ID, () -> scratchesAndConsoles(), AllIcons.Scope.Scratches, new AbstractPackageSet(scratchesAndConsoles()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
        return ScratchesNamedScope.contains(project, file);
      }
    });
  }

  public static boolean contains(@NotNull Project project, @NotNull VirtualFile file) {
    RootType rootType = RootType.forFile(file);
    return rootType != null && !(rootType.isHidden() || rootType.isIgnored(project, file));
  }
}
