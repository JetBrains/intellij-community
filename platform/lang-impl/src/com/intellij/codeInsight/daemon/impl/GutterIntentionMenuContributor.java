// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class GutterIntentionMenuContributor implements IntentionMenuContributor {
  @Override
  public void collectActions(@NotNull Editor hostEditor,
                             @NotNull PsiFile hostFile,
                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                             int passIdToShowIntentionsFor,
                             int offset) {
    Project project = hostFile.getProject();
    Document hostDocument = hostEditor.getDocument();
    int line = hostDocument.getLineNumber(offset);
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(hostDocument, project, true);
    List<RangeHighlighterEx> result = new ArrayList<>();
    model.processRangeHighlightersOverlappingWith(hostDocument.getLineStartOffset(line),
                                                  hostDocument.getLineEndOffset(line),
                                                  Processors.cancelableCollectProcessor(result));
    Collection<AnAction> actions = result.stream()
      .map(RangeHighlighter::getGutterIconRenderer)
      .filter(Objects::nonNull)
      .filter(r -> DumbService.getInstance(project).isUsableInCurrentContext(r))
      .flatMap(r -> {
        ActionGroup group = r.getPopupMenuActions();
        List<AnAction> clickActions = Arrays.asList(r.getClickAction(), r.getMiddleButtonClickAction(), r.getRightButtonClickAction());
        if (group == null) return clickActions.stream();
        AnAction[] children = group instanceof DefaultActionGroup o ? o.getChildren(ActionManager.getInstance()) :
                              group.getChildren(null);
        return ContainerUtil.append(clickActions, children).stream();
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    intentions.guttersToShow.addAll(actions);
  }
}
