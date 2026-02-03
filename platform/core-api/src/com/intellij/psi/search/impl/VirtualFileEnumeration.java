// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * An internal interface to perform index search optimization based on scope.
 * It represents file enumeration which contains whole {@link GlobalSearchScope}
 *
 * You definitely don't need to use it.
 */
@ApiStatus.Internal
public interface VirtualFileEnumeration {
  boolean contains(int fileId);

  int @NotNull [] asArray();

  default @Nullable @Unmodifiable Collection<VirtualFile> getFilesIfCollection() {
    return null;
  }

  static @Nullable VirtualFileEnumeration extract(@NotNull GlobalSearchScope scope) {
    if (scope instanceof VirtualFileEnumeration) {
      return (VirtualFileEnumeration)scope;
    }
    if (scope instanceof VirtualFileEnumerationAware) {
      return ((VirtualFileEnumerationAware)scope).extractFileEnumeration();
    }
    return null;
  }

  VirtualFileEnumeration EMPTY = new VirtualFileEnumeration() {
    @Override
    public boolean contains(int fileId) {
      return false;
    }
    @Override
    public int @NotNull [] asArray() {
      return new int[0];
    }
  };
}

