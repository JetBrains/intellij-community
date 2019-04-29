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

package com.intellij.codeHighlighting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TextEditorHighlightingPassRegistrar {
  public enum Anchor {
    FIRST, LAST, BEFORE, AFTER,
  }

  public static TextEditorHighlightingPassRegistrar getInstance(Project project){
    return ServiceManager.getService(project, TextEditorHighlightingPassRegistrar.class);
  }

  /**
   * Registers the factory for the new highlighting pass.
   * Factory will be asked to create the highlighting pass every time IDEA tries to highlight the file.
   *
   * @param anchorPassId                 id of the anchor pass. Predefined pass Ids are declared in {@link Pass}
   * @return the id of the new pass which e.g. can be used as an anchor for the other pass.
   */
  public int registerTextEditorHighlightingPass(final TextEditorHighlightingPassFactory factory,
                                                final Anchor anchor,
                                                final int anchorPassId,
                                                boolean needAdditionalIntentionsPass,
                                                boolean inPostHighlightingPass) {
    int[] ids = null;
    switch (anchor) {
      case AFTER:
        ids = new int[]{anchorPassId};
        break;
      case BEFORE:
        //todo
        ids = null;
        break;
      case FIRST:
        ids = null;
        break;
      case LAST:
        //todo
        ids = new int[]{Pass.UPDATE_ALL,
          Pass.UPDATE_FOLDING, Pass.LINE_MARKERS,
          Pass.EXTERNAL_TOOLS,
          Pass.LOCAL_INSPECTIONS, Pass.POPUP_HINTS};
        break;
    }
    return registerTextEditorHighlightingPass(factory, ids, null, needAdditionalIntentionsPass, -1);
  }

  public abstract int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory, 
                                                         @Nullable final int[] runAfterCompletionOf,
                                                         @Nullable int[] runAfterStartingOf,
                                                         boolean runIntentionsPassAfter,
                                                         int forcedPassId);
}
