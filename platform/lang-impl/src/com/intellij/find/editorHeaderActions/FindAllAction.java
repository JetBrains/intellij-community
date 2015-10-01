/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:53
* To change this template use File | Settings | File Templates.
*/
public class FindAllAction extends AnAction implements ShortcutProvider, DumbAware {
  public FindAllAction() {
    getTemplatePresentation().setDescription("Export matches to Find tool window");
    getTemplatePresentation().setText("Find All");
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);

    updateTemplateIcon(search);
    e.getPresentation().setIcon(getTemplatePresentation().getIcon());
    e.getPresentation().setEnabled(editor != null && project != null && search != null &&
                                   !project.isDisposed() && search.hasMatches() &&
                                   PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    EditorSearchSession search = e.getRequiredData(EditorSearchSession.SESSION_KEY);

    if (project.isDisposed()) return;

    FindModel oldModel = FindManager.getInstance(project).getFindInFileModel();
    FindModel newModel = oldModel.clone();
    String text = search.getTextInField();
    if (StringUtil.isEmpty(text)) return;

    newModel.setStringToFind(text);
    FindUtil.findAllAndShow(project, editor, newModel);
  }

  @Nullable
  @Override
  public ShortcutSet getShortcut() {
    AnAction findUsages = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
    return findUsages != null ? findUsages.getShortcutSet() : null;
  }

  private void updateTemplateIcon(@Nullable EditorSearchSession session) {
    if (session == null || getTemplatePresentation().getIcon() != null) return;

    Icon base = AllIcons.Actions.Find;
    Icon text = IconUtil.textToIcon("ALL", session.getComponent(), JBUI.scale(6F));

    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(base, 0);
    icon.setIcon(text, 1, 0, base.getIconHeight() - text.getIconHeight());
    getTemplatePresentation().setIcon(icon);
  }
}
