// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class IntentionsUIImpl implements IntentionsUI {

  private volatile IntentionHintComponent myLastIntentionHint;

  public IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  @Override
  public void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged) {
    Editor editor = cachedIntentions.getEditor();
    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().hasFocus()) return;
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false)) return;
    Project project = cachedIntentions.getProject();
    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point xy = editor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;
    IntentionHintComponent hintComponent = myLastIntentionHint;
    boolean hasToRecreate = cachedIntentions.showBulb() &&
                            hintComponent != null &&
                            hintComponent.isForEditor(editor) &&
                            hintComponent.getPopupUpdateResult(actionsChanged) == IntentionHintComponent.PopupUpdateResult.CHANGED_INVISIBLE;

    if (!editor.getSettings().isShowIntentionBulb()) {
      return;
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    hide();

    if (editor.getCaretModel().getCaretCount() > 1) return;

    hintComponent = IntentionHintComponent.showIntentionHint(project, cachedIntentions.getFile(), editor, false, cachedIntentions);
    if (hasToRecreate) {
      hintComponent.recreate();
    }
    myLastIntentionHint = hintComponent;
  }

  @Override
  public void hide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && hint.isVisible()) {
      hint.hide();
      myLastIntentionHint = null;
    }
  }
}
