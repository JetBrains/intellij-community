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

package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class AutoPopupController implements Disposable {
  private final Project myProject;
  private final Alarm myAlarm = new Alarm();

  public static AutoPopupController getInstance(Project project){
    return ServiceManager.getService(project, AutoPopupController.class);
  }

  public AutoPopupController(Project project) {
    myProject = project;
    setupListeners();
  }

  private void setupListeners() {
    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener() {
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        myAlarm.cancelAllRequests();
      }

      public void beforeEditorTyping(char c, DataContext dataContext) {
        myAlarm.cancelAllRequests();
      }


      public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      }
    }, this);

    IdeEventQueue.getInstance().addActivityListener(new Runnable() {
      public void run() {
        myAlarm.cancelAllRequests();
      }
    }, this);
  }

  public void autoPopupMemberLookup(final Editor editor, @Nullable final Condition<Editor> condition){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_MEMBER_LOOKUP) {
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
      if (file == null) return;
      final Runnable request = new Runnable(){
        public void run(){
          if (myProject.isDisposed()) return;
          if (editor.isDisposed()) return;

          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          if (condition != null && !condition.value(editor)) return;
          new CodeCompletionHandlerBase(CompletionType.BASIC, false, false).invoke(myProject, editor, file);
        }
      };
      // invoke later prevents cancelling request by keyPressed from the same action
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myAlarm.addRequest(request, settings.MEMBER_LOOKUP_DELAY);
            }
          });
    }
  }

  public void invokeAutoPopupRunnable(final Runnable request, final int delay) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.closeAndFinish();
    }

    // invoke later prevents cancelling request by keyPressed from the same action
    ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myAlarm.addRequest(request, delay);
          }
        });
  }

  public void autoPopupParameterInfo(final Editor editor, final PsiElement highlightedMethod){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    ApplicationManager.getApplication().assertIsDispatchThread();
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_PARAMETER_INFO) {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      PsiFile file = documentManager.getPsiFile(editor.getDocument());
      if (file == null) return;

      if (!documentManager.isUncommited(editor.getDocument())) {
        file = documentManager.getPsiFile(InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file).getDocument());
        if (file == null) return;
      }

      final PsiFile file1 = file;
      final Runnable request = new Runnable(){
        public void run(){
          if (!editor.isDisposed()) {
            documentManager.commitAllDocuments();
            int lbraceOffset = editor.getCaretModel().getOffset() - 1;
            new ShowParameterInfoHandler().invoke(myProject, editor, file1, lbraceOffset, highlightedMethod);
          }
        }
      };
      // invoke later prevents cancelling request by keyPressed from the same action
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myAlarm.addRequest(request, settings.PARAMETER_INFO_DELAY);
            }
          });
    }
  }

  public void dispose() {
  }
}
