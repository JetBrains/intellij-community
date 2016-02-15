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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Allows to suppress stripping spaces from some lines when a document is being saved and "Strip spaces on Save" option is not "None".
 */
public abstract class StripTrailingSpacesLineFilter {
  public static final ExtensionPointName<StripTrailingSpacesLineFilter> LINE_FILTER_EXTENSION_POINT 
    = new ExtensionPointName<StripTrailingSpacesLineFilter>("com.intellij.stripTrailingSpacesLineFilter");

  /**
   * Tells if spaces can be stripped from the document. Returns <code>false</code> if trailing spaces should be preserved regardless of
   * the editor settings and other filters.
   * 
   * @param project  The current project or <code>null</code> if there is no project context.
   * @param document The document.
   * @return True if it's OK to strip spaces, false otherwise.
   */
  public abstract boolean isStripSpacesAllowed(@Nullable Project project, @NotNull Document document);

  /**
   * Processes a document and sets bits to 1 (true) for lines which should remain untouched when trailing spaces are removed.
   * 
   * @param project   The current project or null if there is no project context.
   * @param document  The document to process.
   * @param disabledLinesBitSet The bit set which can be modified by the <code>apply()</code> method. Each bit index corresponds to a line
   *
   * @return True if successful, false if can't be applied now, probably later. 
   */
  public abstract boolean apply(@Nullable Project project, @NotNull Document document, @NotNull BitSet disabledLinesBitSet);
  
}
