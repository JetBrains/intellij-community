package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.DotAutoLookupHandler;
import com.intellij.codeInsight.completion.JavadocAutoLookupHandler;
import com.intellij.codeInsight.completion.XmlAutoLookupHandler;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Alarm;

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

  protected void setupListeners() {
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

  public void autoPopupMemberLookup(final Editor editor){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_MEMBER_LOOKUP) {
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;
      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          new WriteCommandAction(myProject) {
            protected void run(Result result) throws Throwable {
              if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
              new DotAutoLookupHandler().invoke(myProject, editor, file);
            }
          }.execute();
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

  public void autoPopupXmlLookup(final Editor editor){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_XML_LOOKUP) {
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;
      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              new XmlAutoLookupHandler().invoke(myProject, editor, file);
            }
          }, null, null);
        }
      };
      // invoke later prevents cancelling request by keyPressed from the same action
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myAlarm.addRequest(request, settings.XML_LOOKUP_DELAY);
            }
          });
    }
  }

  public void autoPopupParameterInfo(final Editor editor, final PsiElement highlightedMethod){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    ApplicationManager.getApplication().assertIsDispatchThread();
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_PARAMETER_INFO) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;

      final PsiFile injectedFile = PsiDocumentManager.getInstance(myProject).getPsiFile(InjectedLanguageUtil.getEditorForInjectedLanguage(editor, file).getDocument());
      if (injectedFile == null) return;

      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          int lbraceOffset = editor.getCaretModel().getOffset() - 1;
          new ShowParameterInfoHandler().invoke(myProject, editor, injectedFile, lbraceOffset, highlightedMethod);
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

  public void autoPopupJavadocLookup(final Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_JAVADOC_LOOKUP) {
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;
      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          CommandProcessor.getInstance().executeCommand(
              myProject, new Runnable() {
              public void run(){
                new JavadocAutoLookupHandler().invoke(myProject, editor, file);
              }
            },
            "",
            null
          );
        }
      };
      // invoke later prevents cancelling request by keyPressed from the same action
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myAlarm.addRequest(request, settings.JAVADOC_LOOKUP_DELAY);
            }
          });
    }
  }

  public void dispose() {
  }
}
