// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated A temporary solution to extract formatter module
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public abstract class EditorFacade {
  /**
   * This key is used as a flag that indicates if {@code 'wrap long line during formatting'} activity is performed now.
   */
  public static final Key<Boolean> WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY
    = new Key<>("WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY");

  public static EditorFacade getInstance() {
    return ApplicationManager.getApplication().getService(EditorFacade.class);
  }

  public abstract void runWithAnimationDisabled(@NotNull Editor editor, @NotNull Runnable taskWithScrolling);

  public abstract void undo(@NotNull Project project, @NotNull FileEditor editor, @NotNull Document document, long modificationStamp);

  public abstract void wrapLongLinesIfNecessary(@NotNull PsiFile file,
                                                @NotNull Document document,
                                                int startOffset,
                                                int endOffset,
                                                List<? extends TextRange> enabledRanges,
                                                int rightMargin);

  public abstract void doWrapLongLinesIfNecessary(@NotNull final Editor editor,
                                                  @NotNull final Project project,
                                                  @NotNull Document document,
                                                  int startOffset,
                                                  int endOffset,
                                                  List<? extends TextRange> enabledRanges,
                                                  int rightMargin);
}
