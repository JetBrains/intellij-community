// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.NotNullList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class UnwrapHandler implements CodeInsightActionHandler {
  public static final int HIGHLIGHTER_LEVEL = HighlighterLayer.SELECTION + 1;

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    List<MyUnwrapAction> options = collectOptions(project, editor, file);
    selectOption(options, editor, file);
  }

  @NotNull
  private static List<MyUnwrapAction> collectOptions(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    List<MyUnwrapAction> result = new ArrayList<>();

    UnwrapDescriptor descriptor = getUnwrapDescription(file);

    for (Pair<PsiElement, Unwrapper> desc : descriptor.collectUnwrappers(project, editor, file)) {
      PsiElement element = desc.getFirst();
      Unwrapper unwrapper = desc.getSecond();
      if (element == null || unwrapper == null) {
        throw new IllegalStateException(descriptor + " returned "+desc);
      }
      result.add(createUnwrapAction(unwrapper, element, editor, project));
    }

    return result;
  }

  private static UnwrapDescriptor getUnwrapDescription(@NotNull PsiFile file) {
    return LanguageUnwrappers.INSTANCE.forLanguage(file.getLanguage());
  }

  private static MyUnwrapAction createUnwrapAction(@NotNull Unwrapper u, @NotNull PsiElement el, @NotNull Editor ed, @NotNull Project p) {
    return new MyUnwrapAction(p, ed, u, el);
  }

  protected void selectOption(List<? extends MyUnwrapAction> options, Editor editor, PsiFile file) {
    if (options.isEmpty()) return;

    if (!getUnwrapDescription(file).showOptionsDialog() ||
        ApplicationManager.getApplication().isUnitTestMode()
       ) {
      options.get(0).perform();
      return;
    }

    showPopup(options, editor);
  }

  private static void showPopup(final List<? extends AnAction> options, Editor editor) {
    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);

    List<MyItem> model = ContainerUtil.map(options, a -> new MyItem(((MyUnwrapAction)a).getName(), options.indexOf(a)));
    Function<MyItem, MyUnwrapAction> optionByName = item -> (MyUnwrapAction)options.get(item.index);

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(model)
      .setTitle(CodeInsightBundle.message("unwrap.popup.title"))
      .setMovable(false)
      .setNamerForFiltering(item -> item.name)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback((selectedValue) -> optionByName.apply(selectedValue).perform())
      .setItemSelectedCallback(item -> {
        if (item != null) {
          MyUnwrapAction a = optionByName.apply(item);
          List<PsiElement> toExtract = new NotNullList<>();
          PsiElement wholeRange = a.collectAffectedElements(toExtract);
          highlighter.highlight(wholeRange, toExtract);
        }
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          highlighter.dropHighlight();
        }
      })
      .createPopup().showInBestPositionFor(editor);
  }

  private record MyItem(String name, int index) {
    @Override
    public String toString() {
      return name;
    }
  }

  protected static class MyUnwrapAction extends AnAction {
    private static final Key<Integer> CARET_POS_KEY = new Key<>("UNWRAP_HANDLER_CARET_POSITION");

    private final Project myProject;
    private final Editor myEditor;
    private final Unwrapper myUnwrapper;
    @NotNull
    private final PsiElement myElement;

    MyUnwrapAction(@NotNull Project project, @NotNull Editor editor, @NotNull Unwrapper unwrapper, @NotNull PsiElement element) {
      super(unwrapper.getDescription(element));
      myProject = project;
      myEditor = editor;
      myUnwrapper = unwrapper;
      myElement = element;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      perform();
    }

    public void perform() {
      final PsiFile file = myElement.getContainingFile();
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

      CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
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
      }), null, myEditor.getDocument());
    }

    private void saveCaretPosition(PsiFile file) {
      int offset = myEditor.getCaretModel().getOffset();
      PsiElement el = file.findElementAt(offset);
      if (el == null) return;
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

    private void highlightExtractedElements(final List<? extends PsiElement> extractedElements) {
      for (PsiElement each : extractedElements) {
        final TextRange textRange = each.getTextRange();
        HighlightManager.getInstance(myProject).addRangeHighlight(
            myEditor,
            textRange.getStartOffset(),
            textRange.getEndOffset(),
            EditorColors.SEARCH_RESULT_ATTRIBUTES,
            false,
            true,
            null);
      }
    }

    public String getName() {
      return myUnwrapper.getDescription(myElement);
    }

    PsiElement collectAffectedElements(@NotNull List<? super PsiElement> toExtract) {
      return myUnwrapper.collectAffectedElements(myElement, toExtract);
    }
  }
}
