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

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:53
* To change this template use File | Settings | File Templates.
*/
public class FindAllAction extends EditorHeaderAction implements DumbAware {
  public FindAllAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    Icon base = AllIcons.Actions.Find;
    Icon text = IconUtil.textToIcon("ALL", editorSearchComponent, JBUI.scale(6F));
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(base, 0);
    icon.setIcon(text, 1, 0, base.getIconHeight() - text.getIconHeight());
    getTemplatePresentation().setIcon(icon);
    getTemplatePresentation().setDescription("Export matches to Find tool window");
    getTemplatePresentation().setText("Find All");
    final AnAction findUsages = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
    if (findUsages != null) {
      registerCustomShortcutSet(findUsages.getShortcutSet(),
                                editorSearchComponent.getSearchField());
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    Editor editor = getEditorSearchComponent().getEditor();
    Project project = editor.getProject();
    if (project != null && !project.isDisposed()) {
      e.getPresentation().setEnabled(getEditorSearchComponent().hasMatches() &&
                                     PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Editor editor = getEditorSearchComponent().getEditor();
    Project project = editor.getProject();
    if (project != null && !project.isDisposed()) {
      final FindModel model = FindManager.getInstance(project).getFindInFileModel();
      final FindModel realModel = (FindModel)model.clone();
      String text = getEditorSearchComponent().getTextInField();
      if (StringUtil.isEmpty(text)) return;
      realModel.setStringToFind(text);
      FindUtil.findAllAndShow(project, editor, realModel);
    }
  }
}
