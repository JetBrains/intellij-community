/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.ui.UIUtil;

import java.util.Set;

/**
 * @author peter
*/
public class InvokeTemplateAction extends AnAction {
  private final TemplateImpl myTemplate;
  private final Editor myEditor;
  private final Project myProject;

  public InvokeTemplateAction(final TemplateImpl template, final Editor editor, final Project project, final Set<Character> usedMnemonicsSet) {
    super(extractMnemonic(template.getKey(), usedMnemonicsSet) + ". " + template.getDescription());
    myTemplate = template;
    myProject = project;
    myEditor = editor;
  }

  public static String extractMnemonic(String caption, Set<Character> usedMnemonics) {
    if (StringUtil.isEmpty(caption)) return "";

    for (int i = 0; i < caption.length(); i++) {
      char c = caption.charAt(i);
      if (usedMnemonics.add(Character.toUpperCase(c))) {
        return caption.substring(0, i) + UIUtil.MNEMONIC + caption.substring(i);
      }
    }

    return caption + " ";
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  public void actionPerformed(AnActionEvent e) {
    final Document document = myEditor.getDocument();
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(file);
    }

    String selectionString = myEditor.getSelectionModel().getSelectedText();

    if (selectionString != null) {
      if (myTemplate.isToReformat()) selectionString = selectionString.trim();
    }

    TemplateManager.getInstance(myProject).startTemplate(myEditor, selectionString, myTemplate);
  }
}
