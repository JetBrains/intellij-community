// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class IntentionsUIImpl extends IntentionsUI {
  @ApiStatus.Internal
  public static final Key<Integer> SHOW_INTENTION_BULB_ON_ANOTHER_LINE = Key.create("IntentionsUIImpl.SHOW_INTENTION_BULB_ON_ANOTHER_LINE");

  private volatile IntentionHintComponent myLastIntentionHint;

  public IntentionsUIImpl(@NotNull Project project) {
    super(project);
  }

  IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  @Override
  @RequiresEdt
  public void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged) {
    Editor editor = cachedIntentions.getEditor();
    if (editor == null || editor instanceof ImaginaryEditor) {
      return;
    }
    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().hasFocus()) {
      return;
    }
    if (!actionsChanged) {
      return;
    }

    Project project = cachedIntentions.getProject();

    LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point xy = editor.logicalPositionToXY(caretPos);

    hide();
    if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false)
        && visibleArea.contains(xy)
        && !editor.isViewer()
        && editor.getSettings().isShowIntentionBulb()
        && editor.getCaretModel().getCaretCount() == 1
        && cachedIntentions.showBulb()
        // do not show bulb when the user explicitly ESCaped it away
        && !DaemonCodeAnalyzerEx.getInstanceEx(project).isEscapeJustPressed()) {
      myLastIntentionHint = IntentionHintComponent.showIntentionHint(project, cachedIntentions.getFile(), editor, false, cachedIntentions);
    }
  }

  @Override
  @RequiresEdt
  public void hide() {
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && !hint.isDisposed() && hint.isVisible()) {
      hint.hide();
    }
    myLastIntentionHint = null;
  }

  @Override
  @RequiresEdt
  public void hideForEditor(@NotNull Editor editor) {
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && hint.hideIfDisplayedForEditor(editor)) {
      myLastIntentionHint = null;
    }
  }
}
