// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Add an inlay to current editor. Great to mock new inlays for presentations, etc.
 */
final class AddInlayInternalAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    EditorImpl editor = ObjectUtils.tryCast(e.getData(CommonDataKeys.EDITOR), EditorImpl.class);
    if (editor == null) return;
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.isEmpty()) return;

    Pair<String, Boolean> res =
      Messages.showInputDialogWithCheckBox("Inlay text:", carets.size() > 1 ? "Add Inlays" : "Add Inlay",
                                           "Relates to preceding text", false, true,
                                           Messages.getInformationIcon(), null, new NonEmptyInputValidator());
    String inlayText = res.getFirst();
    boolean relatesToPrecedingText = res.getSecond();
    if (inlayText == null) return;

    int[] offsets = StreamEx.of(carets).mapToInt(Caret::getOffset).toArray();
    InlayModel model = editor.getInlayModel();
    for (int offset : offsets) {
      AtomicReference<Inlay<?>> ref = new AtomicReference<>();
      MenuOnClickPresentation presentation = new MenuOnClickPresentation(new PresentationFactory(editor).text(inlayText), project, () -> {
        return Collections.singletonList(new AnAction(InternalActionsBundle.messagePointer("action.AddInlayInternalAction.Anonymous.text.remove"),
                                                      InternalActionsBundle.messagePointer("action.AddInlayInternalAction.Anonymous.description.remove.this.inlay"),
                                                      AllIcons.Actions.Cancel) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e1) {
            Inlay<?> inlay = ref.get();
            if (inlay != null) {
              Disposer.dispose(inlay);
            }
          }
        });
      });
      Inlay<?> inlay = model.addInlineElement(offset, relatesToPrecedingText, new PresentationRenderer(presentation));
      ref.set(inlay);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    EditorImpl editor = ObjectUtils.tryCast(e.getData(CommonDataKeys.EDITOR), EditorImpl.class);
    presentation.setEnabled(project != null && editor != null);
  }
}
