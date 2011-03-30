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
package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

import java.awt.event.InputEvent;

/**
 * @author peter
 */
public abstract class BaseCodeCompletionAction extends AnAction implements HintManagerImpl.ActionToIgnore, DumbAware {
  private CompletionType myCompletionType;

  protected BaseCodeCompletionAction(CompletionType completionType) {
    myCompletionType = completionType;
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    switch (myCompletionType) {
      case BASIC: FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
      case SMART: FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL);
      case CLASS_NAME: FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CLASSNAME);
    }

    final InputEvent inputEvent = e.getInputEvent();
    new CodeCompletionHandlerBase(myCompletionType).invokeCompletion(project, editor, 1, inputEvent != null && inputEvent.getModifiers() != 0);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    e.getPresentation().setEnabled(false);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    e.getPresentation().setEnabled(true);
  }
}
