/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public enum HighlighterTargetArea {
  /**
   * The highlighter covers the text from its start offset to its end offset.
   * A background that covers a line break fills the rest of the visual line.
   * When the editor rounds the corners of the highlighting, the background gets rounded corners.
   */
  EXACT_RANGE,
  /**
   * The highlighter covers each line that its range touches, from the line start to the line break included.
   * The background fills the whole visual line.
   * Its corners stay straight, also when the editor rounds the corners of the highlighting.
   */
  LINES_IN_RANGE
}
