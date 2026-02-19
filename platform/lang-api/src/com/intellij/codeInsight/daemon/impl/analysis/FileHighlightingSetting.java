// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.editor.markup.InspectionsLevel;
import org.jetbrains.annotations.NotNull;

public enum FileHighlightingSetting {
  /**
   * @deprecated Do not use. For "do not run highlighting, do not run inspections" setting use {@link #SKIP_HIGHLIGHTING}
   */
  @Deprecated
  NONE,
  SKIP_HIGHLIGHTING,  // do not run highlighting/annotators nor inspections
  SKIP_INSPECTION,    // run everything except inspections
  ESSENTIAL,          // run essential highlighting only, run the rest on Save All
  FORCE_HIGHLIGHTING; // do run everything

  public static @NotNull FileHighlightingSetting fromInspectionsLevel(@NotNull InspectionsLevel inspectionsLevel)  {
    return switch (inspectionsLevel) {
      case NONE -> SKIP_HIGHLIGHTING;
      case SYNTAX -> SKIP_INSPECTION;
      case ESSENTIAL -> ESSENTIAL;
      case ALL -> FORCE_HIGHLIGHTING;
    };
  }

  public static @NotNull InspectionsLevel toInspectionsLevel(@NotNull FileHighlightingSetting highlightingSetting)  {
    return switch (highlightingSetting) {
      case NONE, SKIP_HIGHLIGHTING -> InspectionsLevel.NONE;
      case SKIP_INSPECTION -> InspectionsLevel.SYNTAX;
      case ESSENTIAL -> InspectionsLevel.ESSENTIAL;
      case FORCE_HIGHLIGHTING -> InspectionsLevel.ALL;
    };
  }

}
