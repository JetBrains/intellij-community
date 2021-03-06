// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public class FilteredNamedScope extends NamedScope {
  public FilteredNamedScope(@NotNull @NonNls String name,
                            Supplier< @Nls String> presentableNameSupplier,
                            @NotNull Icon icon,
                            int priority,
                            @NotNull VirtualFileFilter filter) {
    super(name, presentableNameSupplier, icon, new FilteredPackageSet(name, priority) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        return filter.accept(file);
      }
    });
  }
}
