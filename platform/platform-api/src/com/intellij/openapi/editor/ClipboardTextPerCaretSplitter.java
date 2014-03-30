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
package com.intellij.openapi.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClipboardTextPerCaretSplitter {
  public List<String> split(String input, int caretCount) {
    if (caretCount <= 0) {
      throw new IllegalArgumentException("Caret count must be positive");
    }
    if (caretCount == 1) {
      return Collections.singletonList(input);
    }
    List<String> result = new ArrayList<String>(caretCount);
    String[] lines = input.split("\n", -1);
    for (int i = 0; i < caretCount; i++) {
      if (lines.length == 0) {
        result.add("");
      }
      else if (lines.length == 1) {
        result.add(lines[0]);
      }
      else if (lines.length % caretCount == 0) {
        StringBuilder b = new StringBuilder();
        int linesPerSegment = lines.length / caretCount;
        for (int j = 0; j < linesPerSegment; j++) {
          if (j > 0) {
            b.append('\n');
          }
          b.append(lines[i * linesPerSegment + j]);
        }
        result.add(b.toString());
      }
      else {
        result.add(i < lines.length ? lines[i] : "");
      }
    }
    return result;
  }
}
