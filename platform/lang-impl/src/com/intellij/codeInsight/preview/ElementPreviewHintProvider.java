/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.preview;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.codeInsight.hint.HintManagerImpl.getHintPosition;

public class ElementPreviewHintProvider implements ElementPreviewProvider {
  private static final Logger LOG = Logger.getInstance(ElementPreviewHintProvider.class);

  private static final int HINT_HIDE_FLAGS = HintManager.HIDE_BY_ANY_KEY |
                                             HintManager.HIDE_BY_OTHER_HINT |
                                             HintManager.HIDE_BY_SCROLLING |
                                             HintManager.HIDE_BY_TEXT_CHANGE |
                                             HintManager.HIDE_IF_OUT_OF_EDITOR;
  @Nullable
  private LightweightHint hint;

  @Override
  public boolean isSupportedFile(@NotNull PsiFile psiFile) {
    for (PreviewHintProvider hintProvider : Extensions.getExtensions(PreviewHintProvider.EP_NAME)) {
      if (hintProvider.isSupportedFile(psiFile)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void show(@NotNull PsiElement element, @NotNull Editor editor, @NotNull Point point, boolean keyTriggered) {
    LightweightHint newHint = getHint(element);
    hideCurrentHintIfAny();
    if (newHint == null) {
      return;
    }

    hint = newHint;
    HintManagerImpl.getInstanceImpl().showEditorHint(newHint, editor,
                                                     getHintPosition(newHint, editor, editor.xyToLogicalPosition(point), HintManager.RIGHT_UNDER),
                                                     HINT_HIDE_FLAGS, 0, false);
  }

  private void hideCurrentHintIfAny() {
    if (hint != null) {
      hint.hide();
      hint = null;
    }
  }

  @Override
  public void hide(@Nullable PsiElement element, @NotNull Editor editor) {
    hideCurrentHintIfAny();
  }

  @Nullable
  private static LightweightHint getHint(@NotNull PsiElement element) {
    for (PreviewHintProvider hintProvider : Extensions.getExtensions(PreviewHintProvider.EP_NAME)) {
      JComponent preview;
      try {
        preview = hintProvider.getPreviewComponent(element);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }
      if (preview != null) {
        return new LightweightHint(preview);
      }
    }
    return null;
  }
}