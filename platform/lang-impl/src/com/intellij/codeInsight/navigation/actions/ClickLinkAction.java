// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ClickLinkAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    HyperlinkInfo hyperlink = getLink(editor, project);

    assert hyperlink != null;

    hyperlink.navigate(project);
  }

  @Nullable
  private static HyperlinkInfo getLink(@Nullable Editor editor, @Nullable Project project) {
    if (editor != null && project != null) {
      int offset = editor.getCaretModel().getOffset();
      return EditorHyperlinkSupport.get(editor).getHyperlinkAt(offset);
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    HyperlinkInfo link = getLink(e.getData(CommonDataKeys.EDITOR), e.getProject());
    e.getPresentation().setEnabledAndVisible(link != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
