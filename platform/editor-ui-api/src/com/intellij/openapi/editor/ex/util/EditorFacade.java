// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated A temporary solution to extract formatter module
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public abstract class EditorFacade {

  public static EditorFacade getInstance() {
    return ApplicationManager.getApplication().getService(EditorFacade.class);
  }

  /**
   * @deprecated Use LineWrappingUtil.doWrapLongLinesIfNecessary()
   */
  @Deprecated
  public abstract void doWrapLongLinesIfNecessary(final @NotNull Editor editor,
                                                  final @NotNull Project project,
                                                  @NotNull Document document,
                                                  int startOffset,
                                                  int endOffset,
                                                  List<? extends TextRange> enabledRanges,
                                                  int rightMargin);
}
