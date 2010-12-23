/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.editor.TextChange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Encapsulates logic of merging set of changes into particular text.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/22/10 12:02 PM
 */
public class BulkChangesMerger {

  /**
   * Merges given changes within the given text and returns result.
   * 
   * @param text      text to apply given changes for
   * @param changes   changes to apply to the given text. It's assumed that there are no intersections between them and that they
   *                  are sorted by offsets in ascending order 
   * @return          merge result
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public CharSequence merge(@NotNull char[] text, @NotNull List<TextChange> changes) {
    int newLength = text.length;
    for (TextChange change : changes) {
      newLength += change.getText().length() - (change.getEnd() - change.getStart());
    }
    char[] data = new char[newLength];
    int oldEndOffset = text.length;
    int newEndOffset = data.length;
    for (int i = changes.size() - 1; i >= 0; i--) {
      TextChange change = changes.get(i);
      
      // Copy all unprocessed symbols from initial text that lay after the changed offset.
      int symbolsToMoveNumber = oldEndOffset - change.getEnd();
      System.arraycopy(text, change.getEnd(), data, newEndOffset - symbolsToMoveNumber, symbolsToMoveNumber);
      newEndOffset -= symbolsToMoveNumber;
      
      // Copy all change symbols.
      char[] changeSymbols = change.getChars();
      newEndOffset -= changeSymbols.length;
      System.arraycopy(changeSymbols, 0, data, newEndOffset, changeSymbols.length);
      oldEndOffset = change.getStart();
    }
    
    if (oldEndOffset > 0) {
      System.arraycopy(text, 0, data, 0, oldEndOffset);
    }
    
    return new String(data);
  }
}
