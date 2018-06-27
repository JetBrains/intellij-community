// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConflictUtils {
  public static final Key<String> ACTIVE_REVISION = Key.create("EditorConflict.ActiveRevision");

  public enum ConflictMarkerType {
    BeforeFirst, BeforeMerged, BeforeLast, AfterLast
  }

  @Nullable
  public static ConflictMarkerType getConflictMarkerType(@NotNull String text) {
    assert !text.isEmpty();
    switch (text.charAt(0)) {
      case '<': return ConflictMarkerType.BeforeFirst;
      case '|': return ConflictMarkerType.BeforeMerged;
      case '=': return ConflictMarkerType.BeforeLast;
      case '>': return ConflictMarkerType.AfterLast;
      default: return null;
    }
  }
}
