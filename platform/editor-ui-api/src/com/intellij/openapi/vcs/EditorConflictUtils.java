// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConflictUtils {
  public static final Key<ConflictMarkerType> ACTIVE_CONFLICT_MARKER = Key.create("EditorConflict.ACTIVE_CONFLICT_MARKER");

  public enum ConflictMarkerType {
    BeforeFirst, BeforeMerged, BeforeLast, AfterLast
  }

  @Nullable
  public static ConflictMarkerType getConflictMarkerType(@Nullable String text) {
    if (text == null || text.isEmpty()) return null;
    switch (text.charAt(0)) {
      case '<':
        return ConflictMarkerType.BeforeFirst;
      case '|':
        return ConflictMarkerType.BeforeMerged;
      case '=':
        return ConflictMarkerType.BeforeLast;
      case '>':
        return ConflictMarkerType.AfterLast;
      default:
        return null;
    }
  }

  @NotNull
  public static ConflictMarkerType getActiveMarkerType(@NotNull Project project) {
    return ObjectUtils.notNull(project.getUserData(ACTIVE_CONFLICT_MARKER), ConflictMarkerType.BeforeFirst);
  }

  public static void setActiveMarkerType(@NotNull Project project, @Nullable ConflictMarkerType type) {
    project.putUserData(ACTIVE_CONFLICT_MARKER, type != null ? type : ConflictMarkerType.BeforeFirst);
  }
}
