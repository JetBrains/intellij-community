// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class FileTypeRestrictionScope extends DelegatingGlobalSearchScope implements VirtualFileEnumerationAware {
  private final FileType[] myFileTypes;

  FileTypeRestrictionScope(@NotNull GlobalSearchScope scope, FileType @NotNull [] fileTypes) {
    super(scope);
    myFileTypes = fileTypes;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!super.contains(file)) return false;

    for (FileType otherFileType : myFileTypes) {
      if (FileTypeRegistry.getInstance().isFileOfType(file, otherFileType)) return true;
    }

    return false;
  }

  @Override
  public @NotNull GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileTypeRestrictionScope) {
      FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
      if (restrict.myBaseScope == myBaseScope) {
        List<FileType> intersection = new ArrayList<>(Arrays.asList(restrict.myFileTypes));
        intersection.retainAll(Arrays.asList(myFileTypes));
        return new FileTypeRestrictionScope(myBaseScope, intersection.toArray(FileType.EMPTY_ARRAY));
      }
    }
    return super.intersectWith(scope);
  }

  @Override
  public @NotNull GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileTypeRestrictionScope) {
      FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
      if (restrict.myBaseScope == myBaseScope) {
        return new FileTypeRestrictionScope(myBaseScope, ArrayUtil.mergeArrays(myFileTypes, restrict.myFileTypes));
      }
    }
    return super.uniteWith(scope);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileTypeRestrictionScope)) return false;
    if (!super.equals(o)) return false;

    FileTypeRestrictionScope that = (FileTypeRestrictionScope)o;

    return Arrays.equals(myFileTypes, that.myFileTypes);
  }

  @Override
  public int calcHashCode() {
    int result = super.calcHashCode();
    result = 31 * result + Arrays.hashCode(myFileTypes);
    return result;
  }

  @Override
  public String toString() {
    return "Restricted by file types: " + Arrays.asList(myFileTypes) + " in (" + myBaseScope + ")";
  }

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    return VirtualFileEnumeration.extract(myBaseScope);
  }
}
