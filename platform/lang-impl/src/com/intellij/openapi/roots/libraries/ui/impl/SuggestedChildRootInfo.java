// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.openapi.roots.libraries.LibraryRootType;
import com.intellij.openapi.roots.libraries.ui.DetectedLibraryRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

final class SuggestedChildRootInfo {
  private final VirtualFile myRootCandidate;
  private final DetectedLibraryRoot myDetectedRoot;
  private final Map<LibraryRootType, String> myRootTypeNames;
  private LibraryRootType mySelectedRootType;

  SuggestedChildRootInfo(@NotNull VirtualFile rootCandidate, @NotNull DetectedLibraryRoot detectedRoot, @NotNull Map<LibraryRootType, String> rootTypeNames) {
    myRootCandidate = rootCandidate;
    myDetectedRoot = detectedRoot;
    myRootTypeNames = rootTypeNames;
    mySelectedRootType = detectedRoot.getTypes().get(0);
  }

  public @NotNull VirtualFile getRootCandidate() {
    return myRootCandidate;
  }

  public @NotNull DetectedLibraryRoot getDetectedRoot() {
    return myDetectedRoot;
  }

  public String getRootTypeName(LibraryRootType type) {
    return myRootTypeNames.get(type);
  }

  public @NotNull LibraryRootType getSelectedRootType() {
    return mySelectedRootType;
  }

  public void setSelectedRootType(String selectedRootType) {
    for (LibraryRootType type : myDetectedRoot.getTypes()) {
      if (getRootTypeName(type).equals(selectedRootType)) {
        mySelectedRootType = type;
        break;
      }
    }
  }

  public String @NotNull [] getRootTypeNames() {
    final String[] types = ArrayUtilRt.toStringArray(myRootTypeNames.values());
    Arrays.sort(types, String.CASE_INSENSITIVE_ORDER);
    return types;
  }
}
