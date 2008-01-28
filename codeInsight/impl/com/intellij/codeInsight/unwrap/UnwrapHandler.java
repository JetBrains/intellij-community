package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class UnwrapHandler implements CodeInsightActionHandler {
  private Project myProject;
  private Editor myEditor;

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    myProject = project;
    myEditor = editor;

    PsiElement el = getSelectedElement(editor, file);

    ArrayList<Action> options = new ArrayList<Action>();
    collectOptions(el, options);
    showOptions(options);
  }

  private PsiElement getSelectedElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    return file.findElementAt(offset);
  }

  private void collectOptions(final PsiElement el, List<Action> result) {
    if (el == null) return;

    Action a = null;
    if (isElseBlock(el) || isElseKeyword(el)) {
      a = new Action("'Else' block") {
        protected void unwrap() throws IncorrectOperationException {
          PsiStatement elseBranch;

          if (isElseKeyword(el)) {
            elseBranch = ((PsiIfStatement)el.getParent()).getElseBranch();
            if (elseBranch == null) return;
          }
          else {
            elseBranch = (PsiStatement)el;
          }

          unwrapElse(elseBranch);
        }
      };
    }
    else if (el instanceof PsiIfStatement) {
      a = new Action("'If' statement") {
        protected void unwrap() throws IncorrectOperationException {
          unwrapIf((PsiIfStatement)el);
        }
      };
    }
    else if (el instanceof PsiCatchSection && tryHasSeveralCatches(el)) {
      a = new Action("'Catch' block") {
        protected void unwrap() throws IncorrectOperationException {
          unwrapCatch((PsiCatchSection)el);
        }
      };
    }
    else if (el instanceof PsiTryStatement) {
      a = new Action("'Try' statement") {
        protected void unwrap() throws IncorrectOperationException {
          unwrapTry((PsiTryStatement)el);
        }
      };
    }

    if (a != null) result.add(a);

    collectOptions(el.getParent(), result);
  }

  private boolean tryHasSeveralCatches(PsiElement el) {
    return ((PsiTryStatement)el.getParent()).getCatchBlocks().length > 1;
  }

  private boolean isElseBlock(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseBranch();
  }

  private boolean isElseKeyword(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseElement();
  }

  private void unwrapIf(PsiIfStatement el) throws IncorrectOperationException {
    PsiStatement then = el.getThenBranch();

    if (then instanceof PsiBlockStatement) {
      extractFromCodeBlock(((PsiBlockStatement)then).getCodeBlock(), el);
    }
    else if (then != null && !(then instanceof PsiEmptyStatement)) {
      extract(new PsiElement[]{then}, el);
    }

    el.delete();
  }

  private void unwrapElse(PsiStatement el) throws IncorrectOperationException {
    if (el instanceof PsiIfStatement) {
      deleteSelectedElseIf((PsiIfStatement)el);
    }
    else {
      el.delete();
    }
  }

  private void deleteSelectedElseIf(PsiIfStatement selectedBranch) throws IncorrectOperationException {
    PsiIfStatement parentIf = (PsiIfStatement)selectedBranch.getParent();
    PsiStatement childElse = selectedBranch.getElseBranch();

    if (childElse == null) {
      selectedBranch.delete();
      return;
    }

    parentIf.setElseBranch(copyElement(childElse));
  }

  private PsiStatement copyElement(PsiStatement el) throws IncorrectOperationException {
    // we can not call el.copy() for 'else' since it sets context to parent 'if'. This cause copy to be invalidated
    // after parent 'if' removal in setElseBranch method.

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    return factory.createStatementFromText(el.getText(), null);
  }

  private void unwrapTry(PsiTryStatement el) throws IncorrectOperationException {
    extractFromCodeBlock(el.getTryBlock(), el);
    extractFromCodeBlock(el.getFinallyBlock(), el);

    el.delete();
  }

  private void unwrapCatch(PsiCatchSection el) throws IncorrectOperationException {
    PsiTryStatement tryEl = (PsiTryStatement)el.getParent();
    if (tryEl.getCatchBlocks().length > 1) {
      el.delete();
    }
    else {
      unwrapTry(tryEl);
    }
  }

  private void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
    if (block == null) return;
    extract(block.getStatements(), from);
  }

  private void extract(PsiElement[] elements, PsiElement from) throws IncorrectOperationException {
    if (elements.length == 0) return;

    PsiElement first = elements[0];
    PsiElement last = elements[elements.length - 1];
    from.getParent().addRangeBefore(first, last, from);
  }

  protected void showOptions(List<Action> options) {
    if (options.isEmpty()) return;

    if (options.size() == 1) {
      options.get(0).actionPerformed(null);
      return;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    for (Action each : options) {
      group.add(each);
    }

    DataContext context = DataManager.getInstance().getDataContext(myEditor.getContentComponent());
    ListPopup popup = JBPopupFactory.getInstance().
      createActionGroupPopup("Unwrap", group, context, JBPopupFactory.ActionSelectionAid.NUMBERING, false);

    popup.showInBestPositionFor(myEditor);

  }

  protected abstract class Action extends AnAction {
    protected Action(String text) {
      super(text);
    }

    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                unwrap();
              }
              catch (IncorrectOperationException ex) {
                throw new RuntimeException(ex);
              }
            }
          });
        }
      }, null, null);
    }

    protected abstract void unwrap() throws IncorrectOperationException;
  }
}
