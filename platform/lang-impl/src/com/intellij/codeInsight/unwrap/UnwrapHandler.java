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

package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.List;

public class UnwrapHandler implements CodeInsightActionHandler {
  public static final int HIGHLIGHTER_LEVEL = HighlighterLayer.SELECTION + 1;

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    List<AnAction> options = collectOptions(project, editor, file);
    selectOption(options, editor, file);
  }

  private List<AnAction> collectOptions(Project project, Editor editor, PsiFile file) {
    List<AnAction> result = new ArrayList<AnAction>();

    UnwrapDescriptor d = getUnwrapDescription(file);

    for (Pair<PsiElement, Unwrapper> each : d.collectUnwrappers(project, editor, file)) {
      result.add(createUnwrapAction(each.getSecond(), each.getFirst(), editor, project));
    }

    return result;
  }

  private static UnwrapDescriptor getUnwrapDescription(PsiFile file) {
    return LanguageUnwrappers.INSTANCE.forLanguage(file.getLanguage());
  }

  private AnAction createUnwrapAction(Unwrapper u, PsiElement el, Editor ed, Project p) {
    return new MyUnwrapAction(p, ed, u, el);
  }

  protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
    if (options.isEmpty()) return;

    if (!getUnwrapDescription(file).showOptionsDialog() ||
        ApplicationManager.getApplication().isUnitTestMode()
       ) {
      options.get(0).actionPerformed(null);
      return;
    }

    showPopup(options, editor);
  }

  private void showPopup(final List<AnAction> options, Editor editor) {
    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);

    DefaultListModel m = new DefaultListModel();
    for (AnAction a : options) {
      m.addElement(((MyUnwrapAction)a).getName());
    }

    final JList list = new JBList(m);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setVisibleRowCount(options.size());

    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = list.getSelectedIndex();
        if (index < 0) return;

        MyUnwrapAction a = (MyUnwrapAction)options.get(index);

        List<PsiElement> toExtract = new ArrayList<PsiElement>();
        PsiElement wholeRange = a.collectAffectedElements(toExtract);
        highlighter.highlight(wholeRange, toExtract);
      }
    });

    PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    builder
        .setTitle(CodeInsightBundle.message("unwrap.popup.title"))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            MyUnwrapAction a = (MyUnwrapAction)options.get(list.getSelectedIndex());
            a.actionPerformed(null);
          }
        })
        .addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            highlighter.dropHighlight();
          }
        });

    JBPopup popup = builder.createPopup();
    popup.showInBestPositionFor(editor);
  }

  public static TextAttributes getTestAttributesForExtract() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    return manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
  }

  private static class MyUnwrapAction extends AnAction {
    private static final Key<Integer> CARET_POS_KEY = new Key<Integer>("UNWRAP_HANDLER_CARET_POSITION");

    private final Project myProject;
    private final Editor myEditor;
    private final Unwrapper myUnwrapper;
    private final PsiElement myElement;

    public MyUnwrapAction(Project project, Editor editor, Unwrapper unwrapper, PsiElement element) {
      super(unwrapper.getDescription(element));
      myProject = project;
      myEditor = editor;
      myUnwrapper = unwrapper;
      myElement = element;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final PsiFile file = myElement.getContainingFile();
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                UnwrapDescriptor d = getUnwrapDescription(file);
                if (d.shouldTryToRestoreCaretPosition()) saveCaretPosition(file);
                int scrollOffset = myEditor.getScrollingModel().getVerticalScrollOffset();

                List<PsiElement> extractedElements = myUnwrapper.unwrap(myEditor, myElement);

                if (d.shouldTryToRestoreCaretPosition()) restoreCaretPosition(file);
                myEditor.getScrollingModel().scrollVertically(scrollOffset);

                highlightExtractedElements(extractedElements);
              }
              catch (IncorrectOperationException ex) {
                throw new RuntimeException(ex);
              }
            }
          });
        }
      }, null, myEditor.getDocument());
    }

    private void saveCaretPosition(PsiFile file) {
      int offset = myEditor.getCaretModel().getOffset();
      PsiElement el = file.findElementAt(offset);

      int innerOffset = offset - el.getTextOffset();
      el.putCopyableUserData(CARET_POS_KEY, innerOffset);
    }

    private void restoreCaretPosition(final PsiFile file) {
      ((TreeElement)file.getNode()).acceptTree(new RecursiveTreeElementWalkingVisitor() {
        @Override
        protected void visitNode(TreeElement element) {
          PsiElement el = element.getPsi();
          Integer offset = el.getCopyableUserData(CARET_POS_KEY);

          // continue;
          if (offset != null) {
            myEditor.getCaretModel().moveToOffset(el.getTextOffset() + offset);
            el.putCopyableUserData(CARET_POS_KEY, null);
            return;
          }
          super.visitNode(element);
        }
      });
    }

    private void highlightExtractedElements(final List<PsiElement> extractedElements) {
      for (PsiElement each : extractedElements) {
        HighlightManager.getInstance(myProject).addRangeHighlight(
            myEditor,
            each.getTextOffset(),
            each.getTextOffset() + each.getTextLength(),
            getTestAttributesForExtract(),
            false,
            true,
            null);
      }
    }

    public String getName() {
      return myUnwrapper.getDescription(myElement);
    }

    public PsiElement collectAffectedElements(List<PsiElement> toExtract) {
      return myUnwrapper.collectAffectedElements(myElement, toExtract);
    }
  }
}
