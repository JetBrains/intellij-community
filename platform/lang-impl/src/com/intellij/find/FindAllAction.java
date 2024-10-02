// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public final class FindAllAction extends AnAction implements ShortcutProvider, DumbAware {
  private static Icon getFindIcon() {
    return ExperimentalUI.isNewUI() ? AllIcons.General.OpenInToolWindow : AllIcons.General.Pin_tab;
  }

  public FindAllAction() {
    super(IdeBundle.messagePointer(ExperimentalUI.isNewUI() ? "show.in.find.window.button.name.newui" : "show.in.find.window.button.name"),
          IdeBundle.messagePointer("show.in.find.window.button.description"));
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    Project project = e.getProject();
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
    Editor editor = search != null ? search.getEditor() : null;

    e.getPresentation().setIcon(getIcon(project));
    e.getPresentation().setEnabled(editor != null && project != null &&
                                   !project.isDisposed() && search.hasMatches() &&
                      PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
    if (search == null) return;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(search.getEditor().getDocument());
    if (file == null) return;

    FindModel oldModel = FindManager.getInstance(project).getFindInFileModel();
    FindModel newModel = oldModel.clone();
    String text = search.getTextInField();
    if (StringUtil.isEmpty(text)) return;

    newModel.setStringToFind(text);
    FindUtil.findAllAndShow(project, file, newModel);
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    AnAction findUsages = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
    return findUsages != null ? findUsages.getShortcutSet() : null;
  }

  private static @NotNull Icon getIcon(@Nullable Project project) {
    ToolWindowManager toolWindowManager = project != null ? ToolWindowManager.getInstance(project) : null;
    if (toolWindowManager != null) {
      return toolWindowManager.getLocationIcon(ToolWindowId.FIND, getFindIcon());
    }
    return getFindIcon();
  }
}
