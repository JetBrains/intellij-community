/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  @NotNull
  public static FileHighlightingSetting fromInspectionsLevel(@NotNull InspectionsLevel inspectionsLevel)  {
    switch (inspectionsLevel) {
      case NONE:
        return SKIP_HIGHLIGHTING;
      case SYNTAX:
        return SKIP_INSPECTION;
      case ESSENTIAL:
        return ESSENTIAL;
      case ALL:
      default:
        return FORCE_HIGHLIGHTING;
    }
  }

  @NotNull
  public static InspectionsLevel toInspectionsLevel(@NotNull FileHighlightingSetting highlightingSetting)  {
    switch (highlightingSetting) {
      case SKIP_HIGHLIGHTING:
        return InspectionsLevel.NONE;
      case SKIP_INSPECTION:
        return InspectionsLevel.SYNTAX;
      case ESSENTIAL:
        return InspectionsLevel.ESSENTIAL;
      case FORCE_HIGHLIGHTING:
      default:
        return InspectionsLevel.ALL;
    }
  }

}
