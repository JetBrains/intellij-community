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

import org.jetbrains.annotations.NotNull;

/**
 * Allows to suppress stripping spaces from some lines or from entire document when the document is being saved and
 * "Strip spaces on Save" option is not "None".
 */
public interface StripTrailingSpacesFilter {
  
  StripTrailingSpacesFilter NOT_ALLOWED = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return false;
    }
  };
  
  StripTrailingSpacesFilter POSTPONED = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return false;
    }
  };
  
  StripTrailingSpacesFilter ALL_LINES = new StripTrailingSpacesFilter() {
    @Override
    public boolean isStripSpacesAllowedForLine(int line) {
      return true;
    }
  };

  boolean isStripSpacesAllowedForLine(int line);

}
