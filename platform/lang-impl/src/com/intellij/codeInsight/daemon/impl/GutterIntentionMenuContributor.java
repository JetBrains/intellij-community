// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
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
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    DataContext dataContext = ((EditorEx)hostEditor).getDataContext();
    JBIterable<AnAction> actions = JBIterable.from(result)
      .filterMap(RangeHighlighter::getGutterIconRenderer)
      .filter(r -> !DumbService.isDumb(project) || DumbService.isDumbAware(r))
      .flatten(r -> {
        ActionGroup group = r.getPopupMenuActions();
        JBIterable<AnAction> it = JBIterable.of(r.getClickAction(), r.getMiddleButtonClickAction(), r.getRightButtonClickAction());
        return group == null ? it : it.append(group.getChildren(null));
      });

    JBTreeTraverser<AnAction> traverser = JBTreeTraverser.of(
      o -> o instanceof ActionGroup && !((ActionGroup)o).isPopup() && !((ActionGroup)o).canBePerformed(dataContext)
           ? ((ActionGroup)o).getChildren(null)
           : null);

    int order = 0;
    for (AnAction action : traverser.withRoots(actions).traverse(TreeTraversal.LEAVES_DFS)) {
      if (action == null) continue;
      Icon icon = action.getTemplatePresentation().getIcon();
      if (icon == null) icon = EmptyIcon.ICON_16;
      final GutterIntentionAction gutterAction = new GutterIntentionAction(action, order++, icon);
      if (!gutterAction.isAvailable(dataContext)) continue;
      intentions.guttersToShow.add(new HighlightInfo.IntentionActionDescriptor(gutterAction, Collections.emptyList(), null, icon, null, null, null) {
        @NotNull
        @Override
        public String getDisplayName() {
          return gutterAction.getText();
        }
      });
    }
  }


}
