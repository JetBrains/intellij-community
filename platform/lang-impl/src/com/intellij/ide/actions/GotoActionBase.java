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

package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction = null;

  public final void actionPerformed(AnActionEvent e) {
    LOG.assertTrue (!getClass ().equals (myInAction));
    try {
      myInAction = getClass();
      gotoActionPerformed (e);
    }
    catch (Throwable t) {
      LOG.error(t);
      myInAction = null;
    }
  }

  protected abstract void gotoActionPerformed(AnActionEvent e);

  public final void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(!getClass().equals (myInAction) && project != null && hasContributors(dataContext));
    presentation.setVisible(hasContributors(dataContext));
  }

  protected boolean hasContributors(final DataContext dataContext) {
    return true;
  }

  public static PsiElement getPsiContext(final AnActionEvent e) {
    PsiFile file = e.getData(LangDataKeys.PSI_FILE);
    if (file != null) return file;
    Project project = e.getData(PlatformDataKeys.PROJECT);
    return getPsiContext(project);
  }

  public static PsiElement getPsiContext(final Project project) {
    if (project == null) return null;
    Editor selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (selectedEditor == null) return null;
    Document document = selectedEditor.getDocument();
    return PsiDocumentManager.getInstance(project).getPsiFile(document);
  }
}
