/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: 25.03.2010
 * Time: 1:50:53
 * To change this template use File | Settings | File Templates.
 */
public class WrapWithCustomTemplateAction extends AnAction {
  private final CustomLiveTemplate myTemplate;
  private final Editor myEditor;
  private final PsiFile myFile;

  public WrapWithCustomTemplateAction(CustomLiveTemplate template,
                                      final Editor editor,
                                      final PsiFile file,
                                      final Set<Character> usedMnemonicsSet) {
    super(InvokeTemplateAction.extractMnemonic(template.getTitle(), usedMnemonicsSet));
    myTemplate = template;
    myFile = file;
    myEditor = editor;
  }


  public void actionPerformed(AnActionEvent e) {
    final Document document = myEditor.getDocument();
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      ReadonlyStatusHandler.getInstance(myFile.getProject()).ensureFilesWritable(file);
    }

    String selection = myEditor.getSelectionModel().getSelectedText();

    if (selection != null) {
      selection = selection.trim();
      myTemplate.wrap(selection, new CustomTemplateCallback(myEditor, myFile));
    }
  }
}
