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

package com.intellij.find.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

public class FindUsagesAction extends AnAction {

  public FindUsagesAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    if (usageTargets != null) {
      usageTargets[0].findUsages();
      return;
    }

    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    chooseAmbiguousTargetAndPerform(project, editor, new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        new PsiElement2UsageTargetAdapter(element).findUsages();
        return false;
      }
    });
  }

  public void update(AnActionEvent event){
    FindUsagesInFileAction.updateFindUsagesAction(event);
  }

  static void chooseAmbiguousTargetAndPerform(@NotNull final Project project, final Editor editor,
                                                      PsiElementProcessor<PsiElement> processor) {
    if (editor == null) {
      Messages.showMessageDialog(project, FindBundle.message("find.no.usages.at.cursor.error"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
    }
    else {
      int offset = editor.getCaretModel().getOffset();
      boolean chosen = GotoDeclarationAction.chooseAmbiguousTarget(editor, offset, processor, FindBundle.message("find.usages.ambiguous.title"), null);
      if (!chosen) {
        HintManager.getInstance().showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"));
      }
    }
  }
  
}
