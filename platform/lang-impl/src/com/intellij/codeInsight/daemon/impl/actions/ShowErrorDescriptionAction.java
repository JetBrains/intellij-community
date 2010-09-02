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

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowErrorDescriptionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;

public class ShowErrorDescriptionAction extends BaseCodeInsightAction implements DumbAware {
  private static int width;
  private static boolean shouldShowDescription = false;
  private static boolean descriptionShown = true;

  public ShowErrorDescriptionAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ShowErrorDescriptionHandler(shouldShowDescription ? width : 0);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file) && isEnabledForFile(project, editor, file);
  }

  private static boolean isEnabledForFile(Project project, Editor editor, PsiFile file) {
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info =
      ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), editor.getCaretModel().getOffset(), false);
    return info != null && info.description != null;
  }

  public void beforeActionPerformedUpdate(final AnActionEvent e) {
    super.beforeActionPerformedUpdate(e);
    changeState();
  }

  private static void changeState() {
    if (Comparing.strEqual(ActionManagerEx.getInstanceEx().getPrevPreformedActionId(), IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) {
      shouldShowDescription = descriptionShown;
    } else {
      shouldShowDescription = false;
      descriptionShown = true;
    }
  }

  public static void rememberCurrentWidth(int currentWidth) {
    width = currentWidth;
    descriptionShown = !shouldShowDescription;
  }

}
