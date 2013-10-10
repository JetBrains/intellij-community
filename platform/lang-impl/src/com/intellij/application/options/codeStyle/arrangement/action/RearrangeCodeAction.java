/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Arranges content at the target file(s).
 * 
 * @author Denis Zhdanov
 * @since 8/30/12 10:01 AM
 */
public class RearrangeCodeAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    boolean enabled = file != null && Rearranger.EXTENSION.forLanguage(file.getLanguage()) != null;
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) {
      return;
    }
    
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();
    documentManager.commitDocument(document);
    
    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) {
      return;
    }

    final List<TextRange> ranges = new ArrayList<TextRange>();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      ranges.add(TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
    }
    else if (selectionModel.hasBlockSelection()) {
      int[] starts = selectionModel.getBlockSelectionStarts();
      int[] ends = selectionModel.getBlockSelectionEnds();
      for (int i = 0; i < starts.length; i++) {
        ranges.add(TextRange.create(starts[i], ends[i]));
      }
    }
    else {
      ranges.add(TextRange.create(0, document.getTextLength()));
    }
    
    final ArrangementEngine engine = ServiceManager.getService(project, ArrangementEngine.class);
    try {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          engine.arrange(editor, file, ranges); 
        }
      }, getTemplatePresentation().getText(), null);
    }
    finally {
      documentManager.commitDocument(document);
    }
  }
}
