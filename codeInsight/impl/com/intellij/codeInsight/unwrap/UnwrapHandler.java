package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.List;

public class UnwrapHandler implements CodeInsightActionHandler {
  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    List<AnAction> options = collectOptions(project, editor, file);
    showOptions(options, editor);
  }

  private List<AnAction> collectOptions(Project project, Editor editor, PsiFile file) {
    List<AnAction> result = new ArrayList<AnAction>();
    for (UnwrapDescriptor d : LanguageUnwrappers.INSTANCE.allForLanguage(file.getLanguage())) {
      for (Pair<PsiElement, Unwrapper> each : d.collectUnwrappers(project, editor, file)) {
        result.add(createUnwrapAction(each.getSecond(), each.getFirst(), editor, project));
      }
    }
    return result;
  }

  private AnAction createUnwrapAction(Unwrapper u, PsiElement el, Editor ed, Project p) {
    return new MyUnwrapAction(p, ed, u, el);
  }

  protected void showOptions(final List<AnAction> options, final Editor editor) {
    if (options.isEmpty()) return;

    if (options.size() == 1) {
      options.get(0).actionPerformed(null);
      return;
    }

    showPopup(options, editor);
  }

  private void showPopup(final List<AnAction> options, Editor editor) {
    final MyScopeHighlighter highlighter = new MyScopeHighlighter(editor);

    DefaultListModel m = new DefaultListModel();
    for (AnAction a : options) {
      m.addElement(((MyUnwrapAction)a).getName());
    }

    final JList list = new JList(m);
    list.setVisibleRowCount(options.size());

    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        int index = list.getSelectedIndex();
        if (index < 0) return;

        MyUnwrapAction a = (MyUnwrapAction)options.get(index);
        highlighter.highlight(a.getElement());
      }
    });

    PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    builder
      .setTitle(CodeInsightBundle.message("unwrap.popup.title"))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(new Runnable() {
        public void run() {
          MyUnwrapAction a = (MyUnwrapAction)options.get(list.getSelectedIndex());
          a.actionPerformed(null);
        }
      })
      .addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(JBPopup popup) {
          highlighter.dropHighlight();
        }
      });

    JBPopup popup = builder.createPopup();
    popup.showInBestPositionFor(editor);
  }

  private static class MyScopeHighlighter {
    private Editor myEditor;
    private RangeHighlighter myPreviousHighliter;

    private MyScopeHighlighter(Editor editor) {
      myEditor = editor;
    }

    public void highlight(PsiElement element) {
      dropHighlight();

      myPreviousHighliter = myEditor.getMarkupModel().addRangeHighlighter(
        element.getTextOffset(),
        element.getTextOffset() + element.getTextLength(),
        HighlighterLayer.SELECTION + 1,
        getTestAttributes(),
        HighlighterTargetArea.EXACT_RANGE);
    }

    private TextAttributes getTestAttributes() {
      EditorColorsManager manager = EditorColorsManager.getInstance();
      return manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    }

    public void dropHighlight() {
      if (myPreviousHighliter == null) return;
      myEditor.getMarkupModel().removeHighlighter(myPreviousHighliter);
    }
  }

  private static class MyUnwrapAction extends AnAction {
    private Project myProject;
    private Editor myEditor;
    private Unwrapper myUnwrapper;
    private PsiElement myElement;

    public MyUnwrapAction(Project project, Editor editor, Unwrapper unwrapper, PsiElement element) {
      super(unwrapper.getDescription(element));
      myProject = project;
      myEditor = editor;
      myUnwrapper = unwrapper;
      myElement = element;
    }

    public void actionPerformed(AnActionEvent e) {
      if (!CodeInsightUtilBase.prepareFileForWrite(myElement.getContainingFile())) return;

      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                myUnwrapper.unwrap(myEditor, myElement);
              }
              catch (IncorrectOperationException ex) {
                throw new RuntimeException(ex);
              }
            }
          });
        }
      }, null, null);
    }

    public String getName() {
      return myUnwrapper.getDescription(myElement);
    }

    public PsiElement getElement() {
      return myElement;
    }
  }
}
