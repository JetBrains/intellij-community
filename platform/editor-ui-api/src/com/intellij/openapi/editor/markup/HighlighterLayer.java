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
package com.intellij.openapi.editor.markup;

public interface HighlighterLayer {
  int SYNTAX = 1000;
  int CARET_ROW = 2000;
  int ADDITIONAL_SYNTAX = 3000;
  int GUARDED_BLOCKS = 3500;
  int WEAK_WARNING = 3750;
  int WARNING = 4000;
  int ERROR = 5000;
  int ELEMENT_UNDER_CARET = 5500;
  
  /** 
   * The default layer for console filters highlighters
   * Ref: com.intellij.execution.filters.Filter
   * */
  int CONSOLE_FILTER = 5800;
  
  int HYPERLINK = 5900;
  int SELECTION = 6000;

  int FIRST = SYNTAX;
  int LAST = SELECTION;
}

