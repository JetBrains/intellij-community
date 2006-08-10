package com.intellij.debugger.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 2:39:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebuggerStatementEditor extends DebuggerEditorImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerStatementEditor");

  private final EditorTextField myEditor;

  private int    myRecentIdx;

  public DebuggerStatementEditor(Project project, PsiElement context, @NonNls String recentsId) {
    super(project, context, recentsId);
    myRecentIdx = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId()).size();
    myEditor = new EditorTextField("", project, StdFileTypes.JAVA) {
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.setOneLineMode(false);
        editor.setVerticalScrollbarVisible(true);
        return editor;
      }
    };
    setLayout(new BorderLayout());
    add(myEditor, BorderLayout.CENTER);

    DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.add(new ItemAction(IdeActions.ACTION_PREVIOUS_OCCURENCE, this){
      public void actionPerformed(AnActionEvent e) {
        LOG.assertTrue(myRecentIdx >= 0);
        // since recents are stored in a stack, previous item is at currentIndex + 1
        myRecentIdx += 1;
        updateTextFromRecents();
      }

      public void update(AnActionEvent e) {
        LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
        e.getPresentation().setEnabled(myRecentIdx < recents.size());
      }
    });
    actionGroup.add(new ItemAction(IdeActions.ACTION_NEXT_OCCURENCE, this){
      public void actionPerformed(AnActionEvent e) {
        if(LOG.isDebugEnabled()) {
          LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
          LOG.assertTrue(myRecentIdx < recents.size());
        }
        // since recents are stored in a stack, next item is at currentIndex - 1
        myRecentIdx -= 1;
        updateTextFromRecents();
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myRecentIdx > 0);
      }
    });

    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.COMBO_PAGER, actionGroup, false).getComponent(),
        BorderLayout.EAST);

    setText(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
  }

  private void updateTextFromRecents() {
    LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
    LOG.assertTrue(myRecentIdx <= recents.size());
    setText(myRecentIdx < recents.size() ? recents.get(myRecentIdx) : new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getEditor().getContentComponent();
  }

  public TextWithImports getText() {
    return createItem(myEditor.getDocument(), getProject());
  }

  public void setText(TextWithImports text) {
    myEditor.setDocument(createDocument(text));
    final Editor editor = myEditor.getEditor();
    if (editor != null) {
      DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
    }
  }

  public TextWithImports createText(String text, String importsString) {
    return new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, text, importsString);
  }

  private static abstract class ItemAction extends AnAction {
    public ItemAction(String sourceActionName, JComponent component) {
      copyFrom(ActionManager.getInstance().getAction(sourceActionName));
      registerCustomShortcutSet(getShortcutSet(), component);
    }
  }

}
