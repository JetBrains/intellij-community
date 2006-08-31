package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.jsp.WebDirectoryElement;
import org.jetbrains.annotations.NonNls;

public abstract class BaseCompleteMacro implements Macro {
  private final String myName;

  public BaseCompleteMacro(@NonNls String name) {
    myName = name;
  }

  abstract CodeInsightActionHandler getCompletionHandler ();

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myName + "()";
  }

  public String getDefaultValue() {
    return "a";
  }

  public final Result calculateResult(Expression[] params, final ExpressionContext context) {
    return new InvokeActionResult(
      new Runnable() {
        public void run() {
          invokeCompletion(context);
        }
      }
    );
  }

  private void invokeCompletion(final ExpressionContext context) {
    final Project project = context.getProject();
    final Editor editor = context.getEditor();

    CommandProcessor.getInstance().executeCommand(
        project, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          getCompletionHandler().invoke(project, editor, PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
        }
      },
        "",
        null
    );

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!project.isOpen()) return;
        CommandProcessor.getInstance().executeCommand(
            project, new Runnable() {
            public void run() {
              final LookupManager lookupManager = LookupManager.getInstance(project);
              Lookup lookup = lookupManager.getActiveLookup();

              if (lookup != null) {
                lookup.addLookupListener(new MyLookupListener(context));
              }
              else {
                TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
                if (templateState != null) {
                  TextRange range = templateState.getCurrentVariableRange();
                  if (range != null && range.getLength() > 0/* && TemplateEditorUtil.getOffset(editor) == range.getEndOffset()*/) {
                    templateState.nextTab();
                  }
                }
              }
            }
          },
            "",
            null
        );
      }
    });
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    return null;
  }

  private class MyLookupListener extends LookupAdapter {
    private final ExpressionContext myContext;

    public MyLookupListener(ExpressionContext context) {
      myContext = context;
    }

    public void itemSelected(LookupEvent event) {
      LookupItem item = event.getItem();
      if (item == null) return;
      final Project project = myContext.getProject();
      final LookupManager lookupManager = LookupManager.getInstance(project);


      if (item.getAttribute(Expression.AUTO_POPUP_NEXT_LOOKUP) != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    invokeCompletion(myContext);
                  }
                });
        return;
      }

      final PsiElement[] elements = lookupManager.getAllElementsForItem(item);

      boolean goNextTab;

      if (elements == null) {
        goNextTab = true;
      }
      else {
        if (elements.length != 1) {
          goNextTab = false;
        }
        else {
          if (elements[0] instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)elements[0];
            goNextTab = method.getParameterList().getParameters().length == 0;
          }
          else if (elements[0] instanceof WebDirectoryElement) {
            VirtualFile originalVirtualFile = ((WebDirectoryElement) elements[0]).getOriginalVirtualFile();
            goNextTab = originalVirtualFile == null || !originalVirtualFile.isDirectory();
          } else if (elements[0] instanceof PsiDirectory) {
            goNextTab = false;
          } else {
            goNextTab = true;
          }
        }
      }

      if (goNextTab) {
        final Editor editor = myContext.getEditor();
        if (editor != null) {
          TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
          if (templateState != null) {
            templateState.nextTab();
          }
        }
      }
    }
  }
}