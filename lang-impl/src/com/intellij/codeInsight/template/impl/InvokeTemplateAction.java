/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
    super(extractMnemonic(template, usedMnemonicsSet) + ". " + template.getDescription());
    myTemplate = template;
    myProject = project;
    myEditor = editor;
  }

  private static String extractMnemonic(final TemplateImpl template, Set<Character> usedMnemonics) {
    final String key = template.getKey();
    if (StringUtil.isEmpty(key)) return "";

    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (usedMnemonics.add(Character.toUpperCase(c))) {
        return key.substring(0, i) + UIUtil.MNEMONIC + key.substring(i);
      }
    }

    return key + " ";
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
