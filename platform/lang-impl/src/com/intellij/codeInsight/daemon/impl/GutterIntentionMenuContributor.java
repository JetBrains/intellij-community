// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IconUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GutterIntentionMenuContributor implements IntentionMenuContributor {
  @Override
  public void collectActions(@NotNull Editor hostEditor,
                             @NotNull PsiFile hostFile,
                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                             int passIdToShowIntentionsFor,
                             int offset) {
    final Project project = hostFile.getProject();
    final Document hostDocument = hostEditor.getDocument();
    final int line = hostDocument.getLineNumber(offset);
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(hostDocument, project, true);
    List<RangeHighlighterEx> result = new ArrayList<>();
    Processor<RangeHighlighterEx> processor = Processors.cancelableCollectProcessor(result);
    model.processRangeHighlightersOverlappingWith(hostDocument.getLineStartOffset(line),
                                                  hostDocument.getLineEndOffset(line),
                                                  processor);

    addActions(hostEditor, intentions, project, result);
  }

  static void addActions(@NotNull Editor hostEditor,
                         @NotNull ShowIntentionsPass.IntentionsInfo intentions, Project project, List<? extends RangeHighlighterEx> result) {
    AnActionEvent event = GutterIntentionAction.createActionEvent((EditorEx)hostEditor);
    for (RangeHighlighterEx highlighter : result) {
      addActions(project, highlighter, intentions.guttersToShow, event);
    }
  }
  private static void addActions(@NotNull Project project,
                                 @NotNull RangeHighlighterEx info,
                                 @NotNull List<? super HighlightInfo.IntentionActionDescriptor> descriptors,
                                 @NotNull AnActionEvent event) {
    final GutterIconRenderer r = info.getGutterIconRenderer();
    if (r == null || DumbService.isDumb(project) && !DumbService.isDumbAware(r)) {
      return;
    }
    List<HighlightInfo.IntentionActionDescriptor> list = new ArrayList<>();
    AtomicInteger order = new AtomicInteger();
    for (AnAction action : new AnAction[]{r.getClickAction(), r.getMiddleButtonClickAction(), r.getRightButtonClickAction(),
      r.getPopupMenuActions()}) {
      if (action != null) {
        addActions(action, list, r, order, event);
      }
    }
    descriptors.addAll(list);
  }

  private static void addActions(@NotNull AnAction action,
                                 @NotNull List<? super HighlightInfo.IntentionActionDescriptor> descriptors,
                                 @NotNull GutterIconRenderer renderer,
                                 AtomicInteger order,
                                 @NotNull AnActionEvent event) {
    if (action instanceof ActionGroup) {
      for (AnAction child : ((ActionGroup)action).getChildren(null)) {
        addActions(child, descriptors, renderer, order, event);
      }
    }
    Icon icon = action.getTemplatePresentation().getIcon();
    if (icon == null) icon = renderer.getIcon();
    if (icon.getIconWidth() < 16) icon = IconUtil.toSize(icon, 16, 16);
    final GutterIntentionAction gutterAction = new GutterIntentionAction(action, order.getAndIncrement(), icon);
    if (!gutterAction.isAvailable(event)) return;
    descriptors.add(new HighlightInfo.IntentionActionDescriptor(gutterAction, Collections.emptyList(), null, icon) {
      @NotNull
      @Override
      public String getDisplayName() {
        return gutterAction.getText();
      }
    });
  }


}
