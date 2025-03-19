// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

public interface Pass {
  int UPDATE_FOLDING = 1;                                     // CodeFoldingPassFactory
  int POPUP_HINTS = 3;                                        // ShowIntentionsPassFactory
  int UPDATE_ALL = 4;                                         // GeneralHighlightingPassFactory
  int LOCAL_INSPECTIONS = 7;                                  // LocalInspectionsPassFactory
  int EXTERNAL_TOOLS = 8;                                     // ExternalToolPassFactory
  int WOLF = 9;                                               // WolfPassFactory
  int LINE_MARKERS = 11;                                      // LineMarkersPassFactory
  @Deprecated int WHOLE_FILE_LOCAL_INSPECTIONS = 12;          // outdated, not used anymore
  int SLOW_LINE_MARKERS = 13;                                 // SlowLineMarkersPassFactory
  int INJECTED_GENERAL = 14;                                  // InjectedGeneralHighlightingPassFactory

  int LAST_PASS = INJECTED_GENERAL;
}