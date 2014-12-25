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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;

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
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
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

    SelectionModel model = editor.getSelectionModel();
    if (model.hasSelection()) {
      new RearrangeCodeProcessor(file, model).run();
    }
    else {
      new RearrangeCodeProcessor(file).run();
    }
  }
}
