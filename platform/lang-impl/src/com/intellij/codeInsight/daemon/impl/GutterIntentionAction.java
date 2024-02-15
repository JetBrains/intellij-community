// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public class GutterIntentionAction extends AbstractIntentionAction
  implements Comparable<IntentionAction>, Iconable, ShortcutProvider, PriorityAction {

  private final @NotNull Supplier<? extends AnAction> myActionSupplier;
  // do not expose myPresentation
  private final @NotNull Presentation myPresentation = Presentation.newTemplatePresentation();
  private final int myOrder;

  public GutterIntentionAction(@NotNull AnAction action, int order) {
    myActionSupplier = () -> action;
    myOrder = order;
  }

  public GutterIntentionAction(@NotNull Supplier<? extends AnAction> action, int order) {
    myActionSupplier = action;
    myOrder = order;
  }

  public void updateFromPresentation(@NotNull Presentation presentation) {
    myPresentation.copyFrom(presentation, null, true);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
    AnActionEvent event = AnActionEvent.createFromInputEvent(
      relativePoint.toMouseEvent(), ActionPlaces.INTENTION_MENU,
      myPresentation.clone(), EditorUtil.getEditorDataContext(editor));

    AnAction action = getAction();
    if (!ActionUtil.lastUpdateAndCheckDumb(action, event, false)) return;
    ActionUtil.performDumbAwareWithCallbacks(action, event, () ->
      ActionUtil.doPerformActionOrShowPopup(action, event, popup -> {
        popup.showInBestPositionFor(editor);
      }));
  }

  @Override
  public @NotNull Priority getPriority() {
    return getAction() instanceof PriorityAction priority ? priority.getPriority() : Priority.NORMAL;
  }

  @Override
  public @NotNull String getText() {
    //noinspection DialogTitleCapitalization
    return ObjectUtils.notNull(myPresentation.getText(), "");
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
  public final @NotNull AnAction getAction() {
    return myActionSupplier.get();
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return ObjectUtils.notNull(myPresentation.getIcon(), EmptyIcon.ICON_16);
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return getAction().getShortcutSet();
  }
}
