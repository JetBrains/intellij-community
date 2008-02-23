package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.IncorrectOperationException;

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

  private AnAction createUnwrapAction(final Unwrapper u, final PsiElement el, final Editor ed, final Project p) {
    return new MyAnAction(u, el, p, ed);
  }

  protected void showOptions(final List<AnAction> options, final Editor editor) {
    if (options.isEmpty()) return;

    if (options.size() == 1) {
      options.get(0).actionPerformed(null);
      return;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    for (AnAction each : options) {
      group.add(each);
    }

    //final JList list = new JList();
    //PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    //builder.setTitle("Choose statement to remove");
    //ListModel model = new ListModel() {
    //  public int getSize() {
    //    return options.size();
    //  }
    //
    //  public Object getElementAt(final int index) {
    //    return ((MyAnAction)options.get(index)).getName();
    //  }
    //
    //  public void addListDataListener(final ListDataListener l) {
    //  }
    //
    //  public void removeListDataListener(final ListDataListener l) {
    //  }
    //};
    //
    //list.setModel(model);
    //list.addListSelectionListener(new ListSelectionListener() {
    //  public void valueChanged(final ListSelectionEvent e) {
    //    //MyAnAction value = (MyAnAction)list.getSelectedValue();
    //    //if (value == null) return;
    //    //System.out.println(value.getName());
    //    System.out.println(list.getSelectedValue());
    //  }
    //});

    //JBPopup popup = builder.createPopup();

    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);

    DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      "Choose statement to remove",
      group,
      context,
      JBPopupFactory.ActionSelectionAid.NUMBERING,
      false,
      new Runnable() {
        public void run() {
          highlighter.dropHighlight();
        }
      },
      -1);

    final ListPopupImpl impl = (ListPopupImpl)popup;

    impl.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        MyAnAction action = (MyAnAction)options.get(impl.getSelectionIndex());
        highlighter.highlight(action.getElement());
      }
    });

    popup.showInBestPositionFor(editor);
    MyAnAction action = (MyAnAction)options.get(impl.getSelectionIndex());
    highlighter.highlight(action.getElement());
  }

  private static class ScopeHighlighter {
    private Editor myEditor;
    private RangeHighlighter myPreviousHighliter;


    private ScopeHighlighter(Editor editor) {
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

  private static class MyAnAction extends AnAction {
    private final Unwrapper myU;
    private final PsiElement myEl;
    private final Project myP;
    private final Editor myEd;

    public MyAnAction(final Unwrapper u, final PsiElement el, final Project p, final Editor ed) {
      super(u.getDescription(el));
      myU = u;
      myEl = el;
      myP = p;
      myEd = ed;
    }

    public void actionPerformed(AnActionEvent e) {
      if (!CodeInsightUtilBase.prepareFileForWrite(myEl.getContainingFile())) return;

      CommandProcessor.getInstance().executeCommand(myP, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                myU.unwrap(myP, myEd, myEl);
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
      return myU.getDescription(myEl);
    }

    public PsiElement getElement() {
      return myEl;
    }
  }
}
