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
package com.intellij.openapi.editor;

/**
 * Allows to control how many whitespace characters must be left on any given line if not all of them can be removed.
 */
public abstract class SmartStripTrailingSpacesFilter implements StripTrailingSpacesFilter {
  @Override
  public final boolean isStripSpacesAllowedForLine(int line) {
    return getTrailingSpacesToLeave(line) >= 0;
  }

  /**
   * @param line The line for which a number of whitespace characters to leave must be calculated.
   * @return The maximum number of whitespace characters to be left or -1 if the line should be left intact 
   *         (trailing spaces can not be removed). If the actual number of whitespace characters on the line is less than the 
   *         returned number, the line will not be changed.
   */
  public abstract int getTrailingSpacesToLeave(int line);
}
