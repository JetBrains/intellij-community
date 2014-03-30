/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  int CARET_ROW = 1000;
  int SYNTAX = 2000;
  int ADDITIONAL_SYNTAX = 3000;
  int GUARDED_BLOCKS = 3500;
  int WARNING = 4000;
  int ERROR = 5000;
  int ELEMENT_UNDER_CARET = 5500;
  int SELECTION = 6000;

  int FIRST = CARET_ROW;
  int LAST = SELECTION;
}
