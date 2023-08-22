// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeHighlighting;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TextEditorHighlightingPassRegistrar {
  public enum Anchor {
    FIRST, LAST, BEFORE, AFTER,
  }

  public static TextEditorHighlightingPassRegistrar getInstance(Project project) {
    return project.getService(TextEditorHighlightingPassRegistrar.class);
  }

  /**
   * Registers the factory for the new highlighting pass.
   * Factory will be asked to create the highlighting pass every time IDE tries to highlight the file.
   *
   * @param anchorPassId id of the anchor pass. Predefined pass Ids are declared in {@link Pass}
   * @return the id of the new pass which, e.g., can be used as an anchor for the other pass.
   */
  public int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory,
                                                @NotNull Anchor anchor,
                                                int anchorPassId,
                                                boolean needAdditionalIntentionsPass,
                                                boolean inPostHighlightingPass) {
    int[] ids = switch (anchor) {
      case AFTER -> new int[]{anchorPassId};
      case BEFORE ->
        //todo
        null;
      case FIRST -> null;
      case LAST ->
        //todo
        new int[]{Pass.UPDATE_ALL,
          Pass.UPDATE_FOLDING, Pass.LINE_MARKERS, Pass.SLOW_LINE_MARKERS,
          Pass.EXTERNAL_TOOLS,
          Pass.LOCAL_INSPECTIONS, Pass.POPUP_HINTS};
    };
    return registerTextEditorHighlightingPass(factory, ids, null, needAdditionalIntentionsPass, -1);
  }

  public abstract int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory,
                                                         int @Nullable [] runAfterCompletionOf,
                                                         int @Nullable [] runAfterStartingOf,
                                                         boolean runIntentionsPassAfter,
                                                         int forcedPassId);
}
