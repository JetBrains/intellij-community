// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search.actions;

import com.intellij.icons.AllIcons;
import com.intellij.largeFilesEditor.search.searchResultsPanel.RangeSearch;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public class FindFurtherAction extends AnAction implements DumbAware {
  private final boolean directionForward;
  private final RangeSearch myRangeSearch;

  public FindFurtherAction(boolean directionForward, RangeSearch rangeSearch) {
    this.directionForward = directionForward;
    this.myRangeSearch = rangeSearch;

    Supplier<String> text;
    Supplier<String> description;
    Icon icon;

    if (directionForward) {
      text = EditorBundle.messagePointer("large.file.editor.find.further.forward.action.text");
      description = EditorBundle.messagePointer("large.file.editor.find.further.forward.action.description");
      icon = AllIcons.Actions.FindAndShowNextMatches;
    }
    else {
      text = EditorBundle.messagePointer("large.file.editor.find.further.backward.action.text");
      description = EditorBundle.messagePointer("large.file.editor.find.further.backward.action.description");
      icon = AllIcons.Actions.FindAndShowPrevMatches;
    }

    getTemplatePresentation().setText(text);
    getTemplatePresentation().setDescription(description);
    getTemplatePresentation().setIcon(icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = myRangeSearch.isButtonFindFurtherEnabled(directionForward);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myRangeSearch.onClickSearchFurther(directionForward);
  }
}
