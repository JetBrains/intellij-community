// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    JBIterable.from(result)
      .filterMap(RangeHighlighter::getGutterIconRenderer)
      .filter(r -> !DumbService.isDumb(project) || DumbService.isDumbAware(r))
      .flatten(r -> {
        ActionGroup group = r.getPopupMenuActions();
        JBIterable<AnAction> it = JBIterable.of(r.getClickAction(), r.getMiddleButtonClickAction(), r.getRightButtonClickAction());
        return group == null ? it : it.append(group.getChildren(null));
      })
      .filter(Objects::nonNull)
      .addAllTo(intentions.guttersToShow);
  }
}
