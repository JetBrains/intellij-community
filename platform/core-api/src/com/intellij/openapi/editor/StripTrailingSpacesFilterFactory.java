// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows adding restrictions on how trailing spaces will be handled in a document.
 */
public abstract class StripTrailingSpacesFilterFactory {
  public static final ExtensionPointName<StripTrailingSpacesFilterFactory> EXTENSION_POINT
    = new ExtensionPointName<>("com.intellij.stripTrailingSpacesFilterFactory");

  /**
   * Creates a filter which may restrict trailing spaces removal.
   *
   * @param project  The current project or {@code null} if there is no project context.
   * @param document The document to be processed.
   * @return The filter which will be called on document save. The factory may return one of the several predefined filters:
   * <ul>
   * <li>{@link StripTrailingSpacesFilter#NOT_ALLOWED} No stripping allowed. The IDE will not try to strip any whitespace at all in this case.</li>
   * <li>{@link StripTrailingSpacesFilter#POSTPONED} The stripping is not possible at the moment. For example, the caret
   * is in the way and the "Settings|General|Editor|Allow caret after end of the line" is off. In this case, the IDE will try to restart
   * the stripping later.</li>
   * <li>{@link StripTrailingSpacesFilter#ALL_LINES} Allow stripping with no restrictions. Return this value by default.</li>
   * </ul>
   */
  public abstract @NotNull StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document);
}
