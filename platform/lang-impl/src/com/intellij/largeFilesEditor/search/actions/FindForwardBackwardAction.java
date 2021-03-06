// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search.actions;

import com.intellij.icons.AllIcons;
import com.intellij.largeFilesEditor.search.LfeSearchManager;
import com.intellij.largeFilesEditor.search.searchTask.SearchTaskOptions;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class FindForwardBackwardAction extends AnAction implements DumbAware {

  private final LfeSearchManager searchManager;
  private final boolean directionForward;

  public FindForwardBackwardAction(boolean directionForward, LfeSearchManager searchManager) {
    this.directionForward = directionForward;
    this.searchManager = searchManager;

    getTemplatePresentation().setDescription(directionForward ?
                                             EditorBundle.messagePointer("large.file.editor.find.forward.action.description") :
                                             EditorBundle.messagePointer("large.file.editor.find.backward.action.description"));
    getTemplatePresentation().setText(directionForward ?
                                      EditorBundle.messagePointer("large.file.editor.find.forward.action.text") :
                                      EditorBundle.messagePointer("large.file.editor.find.backward.action.text"));
    getTemplatePresentation().setIcon(directionForward ? AllIcons.Actions.FindForward : AllIcons.Actions.FindBackward);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (StringUtil.isEmpty(searchManager.getSearchReplaceComponent().getSearchTextComponent().getText())) {
      return;
    }
    searchManager.launchNewRangeSearch(
      directionForward ? searchManager.getLargeFileEditor().getCaretPageNumber() : SearchTaskOptions.NO_LIMIT,
      directionForward ? SearchTaskOptions.NO_LIMIT : searchManager.getLargeFileEditor().getCaretPageNumber(),
      directionForward);
  }
}
