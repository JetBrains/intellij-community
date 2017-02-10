/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeHighlighting;

public interface Pass {
  int UPDATE_FOLDING = 1;
  int POPUP_HINTS = 3;
  int UPDATE_ALL = 4;
  /** will be removed shortly; use {@link #LINE_MARKERS} instead*/
  @Deprecated
  int UPDATE_OVERRIDDEN_MARKERS = 6;
  int LOCAL_INSPECTIONS = 7;
  int EXTERNAL_TOOLS = 8;
  int WOLF = 9;
  int LINE_MARKERS = 11;
  int WHOLE_FILE_LOCAL_INSPECTIONS = 12;

  int LAST_PASS = WHOLE_FILE_LOCAL_INSPECTIONS;
}