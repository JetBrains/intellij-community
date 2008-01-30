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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.ArrayList;

public class UnwrapHandler implements CodeInsightActionHandler {
  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiElement e = findTargetElement(editor, file);
    if (e == null) return;

    List<AnAction> options = collectOptions(e, editor, project);
    showOptions(options, editor);
  }

  private List<AnAction> collectOptions(PsiElement element, Editor editor, Project project) {
    List<AnAction> result = new ArrayList<AnAction>();
    for (UnwrapDescriptor d : LanguageUnwrappers.INSTANCE.allForLanguage(element.getLanguage())) {
      for (Pair<PsiElement, Unwrapper> each : d.collectUnwrappers(element)) {
        result.add(createUnwrapAction(each.getSecond(), each.getFirst(), editor, project));
      }
    }
    return result;
  }

  public PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    return file.findElementAt(offset);
  }

  private AnAction createUnwrapAction(final Unwrapper u, final PsiElement el, final Editor ed, final Project p) {
    return new AnAction(u.getDescription(el)) {
      public void actionPerformed(AnActionEvent e) {
        CommandProcessor.getInstance().executeCommand(p, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                try {
                  u.unwrap(p, ed, el);
                }
                catch (IncorrectOperationException ex) {
                  throw new RuntimeException(ex);
                }
              }
            });
          }
        }, null, null);
      }
    };
  }

  protected void showOptions(List<AnAction> options, Editor editor) {
    if (options.isEmpty()) return;

    if (options.size() == 1) {
      options.get(0).actionPerformed(null);
      return;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    for (AnAction each : options) {
      group.add(each);
    }

    DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
    ListPopup popup = JBPopupFactory.getInstance().
      createActionGroupPopup("Choose statement to remove", group, context, JBPopupFactory.ActionSelectionAid.NUMBERING, false);

    popup.showInBestPositionFor(editor);
  }
}
