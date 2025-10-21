// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

final class FileTypeRestrictionScope extends DelegatingGlobalSearchScope implements VirtualFileEnumerationAware {
  private final Object myFileTypes;

  FileTypeRestrictionScope(@NotNull GlobalSearchScope scope, FileType @NotNull [] fileTypes) {
    super(scope);
    if (fileTypes.length == 1) {
      myFileTypes = fileTypes[0];
    } else {
      myFileTypes = fileTypes;
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!super.contains(file)) return false;

    if (myFileTypes instanceof FileType) {
      return FileTypeRegistry.getInstance().isFileOfType(file, (FileType)myFileTypes);
    }

    for (FileType otherFileType : ((FileType[])myFileTypes)) {
      ProgressManager.checkCanceled();
      if (FileTypeRegistry.getInstance().isFileOfType(file, otherFileType)) return true;
    }

    return false;
  }

  @Override
  public @NotNull GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileTypeRestrictionScope) {
      FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
      if (restrict.myBaseScope == myBaseScope) {
        List<FileType> intersection = new ArrayList<>(restrict.getFileTypes());
        intersection.retainAll(getFileTypes());
        if (intersection.isEmpty()) {
          return EMPTY_SCOPE;
        }
        return new FileTypeRestrictionScope(myBaseScope, intersection.toArray(FileType.EMPTY_ARRAY));
      }
    }
    return super.intersectWith(scope);
  }

  private List<FileType> getFileTypes() {
    if (myFileTypes instanceof FileType) {
      return Collections.singletonList((FileType)myFileTypes);
    }
    return Arrays.asList((FileType[])myFileTypes);
  }

  @Override
  public @NotNull GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileTypeRestrictionScope) {
      FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
      if (restrict.myBaseScope == myBaseScope) {
        if (restrict.myFileTypes instanceof FileType && myFileTypes instanceof FileType) {
          if (restrict.myFileTypes == myFileTypes) return this;
        }

        LinkedHashSet<FileType> result = new LinkedHashSet<>(getFileTypes());
        result.addAll(restrict.getFileTypes());
        FileType[] unitedTypes = result.toArray(FileType.EMPTY_ARRAY);
        return new FileTypeRestrictionScope(myBaseScope, unitedTypes);
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

    if (myFileTypes instanceof FileType) {
      return that.myFileTypes instanceof FileType && myFileTypes.equals(that.myFileTypes);
    }
    if (that.myFileTypes instanceof FileType) return false;

    return Arrays.equals(((FileType[])myFileTypes), ((FileType[])that.myFileTypes));
  }

  @Override
  public int calcHashCode() {
    int result = super.calcHashCode();
    result = 31 * result + (myFileTypes instanceof FileType ? myFileTypes.hashCode(): Arrays.hashCode((FileType[])myFileTypes));
    return result;
  }

  @Override
  public String toString() {
    return "Restricted by file types: " + getFileTypes() + " in (" + myBaseScope + ")";
  }

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    return VirtualFileEnumeration.extract(myBaseScope);
  }
}
