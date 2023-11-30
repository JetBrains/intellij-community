// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class GutterIntentionAction extends AbstractIntentionAction implements Comparable<IntentionAction>, Iconable, ShortcutProvider,
                                                                                    PriorityAction {
  private final AnAction myAction;
  private final int myOrder;
  private final Icon myIcon;
  private final @IntentionName String myText;

  public GutterIntentionAction(@NotNull AnAction action, int order, @NotNull Icon icon, @NotNull @IntentionName String text) {
    myAction = action;
    myOrder = order;
    myIcon = icon;
    myText = text;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
    AnActionEvent event = AnActionEvent.createFromInputEvent(
      relativePoint.toMouseEvent(), ActionPlaces.INTENTION_MENU, null, EditorUtil.getEditorDataContext(editor));
    if (!ActionUtil.lastUpdateAndCheckDumb(myAction, event, false)) return;
    ActionUtil.performDumbAwareWithCallbacks(myAction, event, () ->
      ActionUtil.doPerformActionOrShowPopup(myAction, event, popup -> {
        popup.showInBestPositionFor(editor);
      }));
  }

  @Override
  public @NotNull Priority getPriority() {
    return myAction instanceof PriorityAction priority ? priority.getPriority() : Priority.NORMAL;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public int compareTo(@NotNull IntentionAction o) {
    if (o instanceof GutterIntentionAction gutter) {
      return myOrder - gutter.myOrder;
    }
    return 0;
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @NotNull AnAction getAction() {
    return myAction;
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return myIcon;
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return myAction.getShortcutSet();
  }
}
