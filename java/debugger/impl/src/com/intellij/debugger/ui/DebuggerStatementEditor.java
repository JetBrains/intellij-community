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
package com.intellij.debugger.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author lex
 */
public class DebuggerStatementEditor extends DebuggerEditorImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerStatementEditor");

  private final EditorTextField myEditor;

  private int    myRecentIdx;

  public DebuggerStatementEditor(Project project, PsiElement context, @NonNls String recentsId, final CodeFragmentFactory factory) {
    super(project, context, recentsId, factory);
    myRecentIdx = getRecentItemsCount();
    myEditor = new EditorTextField("", project, factory.getFileType()) {
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.setVerticalScrollbarVisible(true);
        return editor;
      }

      @Override
      protected boolean isOneLineMode() {
        return false;
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
        e.getPresentation().setEnabled(myRecentIdx < getRecentItemsCount());
      }
    });
    actionGroup.add(new ItemAction(IdeActions.ACTION_NEXT_OCCURENCE, this){
      public void actionPerformed(AnActionEvent e) {
        if(LOG.isDebugEnabled()) {
          LOG.assertTrue(myRecentIdx < getRecentItemsCount());
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

    setText(new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, ""));
  }

  private void updateTextFromRecents() {
    List<TextWithImports> recents = getRecents();
    LOG.assertTrue(myRecentIdx <= recents.size());
    setText(myRecentIdx < recents.size() ? recents.get(myRecentIdx) : new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
  }

  private List<TextWithImports> getRecents() {
    final LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
    final ArrayList<TextWithImports> reversed = new ArrayList<TextWithImports>(recents.size());
    for (final ListIterator<TextWithImports> it = recents.listIterator(recents.size()); it.hasPrevious();) {
      reversed.add(it.previous());
    }
    return reversed;
  }

  private int getRecentItemsCount() {
    return DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId()).size();
  }

  public JComponent getPreferredFocusedComponent() {
    final Editor editor = myEditor.getEditor();
    return editor != null? editor.getContentComponent() : myEditor;
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
