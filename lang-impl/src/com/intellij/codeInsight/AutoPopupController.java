package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.DotAutoLookupHandler;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
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
      public void beforeActionPerformed(AnAction action, DataContext dataContext) {
        myAlarm.cancelAllRequests();
      }

      public void beforeEditorTyping(char c, DataContext dataContext) {
        myAlarm.cancelAllRequests();
      }


      public void afterActionPerformed(final AnAction action, final DataContext dataContext) {
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
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          if (condition != null && !condition.value(editor)) return;
          new DotAutoLookupHandler().invoke(myProject, editor, file);
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
    final CompletionProgressIndicator currentCompletion = CompletionProgressIndicator.getCurrentCompletion();
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
