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

public class IntentionsUIImpl extends IntentionsUI {
  private volatile IntentionHintComponent myLastIntentionHint;

  public IntentionsUIImpl(@NotNull Project project) {
    super(project);
  }

  IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  @Override
  public void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = cachedIntentions.getEditor();
    if (editor == null) return;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().hasFocus()) return;
    if (!actionsChanged) return;

    Project project = cachedIntentions.getProject();
    LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point xy = editor.logicalPositionToXY(caretPos);

    hide();
    if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false)
        && visibleArea.contains(xy)
        && editor.getSettings().isShowIntentionBulb()
        && editor.getCaretModel().getCaretCount() == 1
        && cachedIntentions.showBulb()
        // do not show bulb when the user explicitly ESCaped it away
        && !DaemonListeners.getInstance(project).isEscapeJustPressed()) {
      myLastIntentionHint = IntentionHintComponent.showIntentionHint(project, cachedIntentions.getFile(), editor, false, cachedIntentions);
    }
  }

  @Override
  public void hide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && !hint.isDisposed() && hint.isVisible()) {
      hint.hide();
    }
    myLastIntentionHint = null;
  }

  @Override
  public void hideForEditor(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && hint.hideIfDisplayedForEditor(editor)) {
      myLastIntentionHint = null;
    }
  }
}
