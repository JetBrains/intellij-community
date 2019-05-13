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
package com.intellij.openapi.editor.colors;

import com.intellij.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

public enum EditorFontType {
  PLAIN,
  BOLD,
  ITALIC,
  BOLD_ITALIC,
  CONSOLE_PLAIN,
  CONSOLE_BOLD,
  CONSOLE_ITALIC,
  CONSOLE_BOLD_ITALIC;

  private static final Map<EditorFontType, EditorFontType> ourConsoleTypes = new HashMap<>();
  static {
    ourConsoleTypes.put(PLAIN, CONSOLE_PLAIN);
    ourConsoleTypes.put(ITALIC, CONSOLE_ITALIC);
    ourConsoleTypes.put(BOLD_ITALIC, CONSOLE_BOLD_ITALIC);
    ourConsoleTypes.put(BOLD, CONSOLE_BOLD);
  }

  public static EditorFontType getConsoleType(EditorFontType fontType) {
    return ObjectUtils.chooseNotNull(ourConsoleTypes.get(fontType), fontType);
  }
}
