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
 * Allows to suppress stripping spaces from some lines or from entire document when the document is being saved and
 * "Settings|Editor|General|Strip trailing spaces on save" option is not "None".
 * 
 * @see StripTrailingSpacesFilterFactory
 */
public interface StripTrailingSpacesFilter {

  /**
   * The filter which completely prohibits removing trailing spaces from a document regardless of other filters.
   */
  StripTrailingSpacesFilter NOT_ALLOWED = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return false;
    }
  };

  /**
   * Tells that strip trailing spaces is not currently possible since some conditions are not met for proper document processing.
   * The filter will force another attempt to strip trailing spaces later regardless of other filters.
   */
  StripTrailingSpacesFilter POSTPONED = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return false;
    }
  };

  /**
   * The filter which does not put any restrictions on trailing spaces removal. Other filters may be taken into account.
   */
  StripTrailingSpacesFilter ALL_LINES = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return true;
    }
  };

  /**
   * @param line  The document line. Lines are from 0 to {@link com.intellij.openapi.editor.Document#getLineCount()} - 1 inclusive.
   * @return True if trailing spaces can be removed from the line, false otherwise.
   */
  boolean isStripSpacesAllowedForLine(int line);
}
