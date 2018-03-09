// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Collection;

public class FilteredNamedScope extends NamedScope {
  public FilteredNamedScope(@NotNull String name, @NotNull Icon icon, int priority, @NotNull Collection<VirtualFile> files) {
    this(name, icon, priority, files::contains);
  }

  public FilteredNamedScope(@NotNull String name, @NotNull Icon icon, int priority, @NotNull VirtualFileFilter filter) {
    super(name, icon, new AbstractPackageSet(name, priority) {
      @Override
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return file != null && filter.accept(file);
      }
    });
  }
}
