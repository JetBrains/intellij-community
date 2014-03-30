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

package com.intellij.codeInsight.documentation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ShowQuickDocInfoAction extends BaseCodeInsightAction implements HintManagerImpl.ActionToIgnore, DumbAware, PopupAction {
  @NonNls public static final String CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE = "codeassists.quickjavadoc.lookup";
  @NonNls public static final String CODEASSISTS_QUICKJAVADOC_FEATURE = "codeassists.quickjavadoc";

  public ShowQuickDocInfoAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        DocumentationManager.getInstance(project).showJavaDocInfo(editor, file, LookupManager.getActiveLookup(editor) == null);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }


  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (editor == null && element == null) {
      presentation.setEnabled(false);
      return;
    }

    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      if (!isValidForLookup()) {
        presentation.setEnabled(false);
      }
      else {
        presentation.setEnabled(true);
      }
    }
    else {
      if (editor != null) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) {
          presentation.setEnabled(false);
        }

        if (element == null && file != null) {
          try {
            final PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
            if (ref instanceof PsiPolyVariantReference) {
              element = ref.getElement();
            }
          }
          catch (IndexNotReadyException e) {
            element = null;
          }
        }
      }

      if (element != null) {
        presentation.setEnabled(true);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

    if (project != null && editor != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_FEATURE);
      final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null) {
        //dumpLookupElementWeights(lookup);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE);
      }
      actionPerformedImpl(project, editor);
    }
    else if (project != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickjavadoc.ctrln");
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          DocumentationManager.getInstance(project).showJavaDocInfo(element, null);
        }
      }, getCommandName(), null);
    }
  }

}
