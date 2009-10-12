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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

/**
 * Interface which should be implemented in order to disable specific range highlighters
 * in specific editor instances.
 *
 * @author max
 * @see RangeHighlighter#setEditorFilter(MarkupEditorFilter)
 */
public interface MarkupEditorFilter {
  MarkupEditorFilter EMPTY = new MarkupEditorFilter() {
    public boolean avaliableIn(Editor editor) {
      return true;
    }
  };

  /**
   * Checks if the highlighter is active in the specified editor.
   *
   * @param editor the editor to check for.
   * @return true if the highlighter is available, false otherwise.
   */
  boolean avaliableIn(Editor editor);
}
