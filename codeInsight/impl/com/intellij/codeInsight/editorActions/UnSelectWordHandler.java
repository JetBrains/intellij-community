package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;

import java.util.List;

/**
 *
 */
public class UnSelectWordHandler extends EditorActionHandler {
  private EditorActionHandler myOriginalHandler;

  public UnSelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    doAction(editor, file);
  }


  private static void doAction(Editor editor, PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      return;
    }

    CharSequence text = editor.getDocument().getCharsSequence();

    int caretOffset = editor.getCaretModel().getOffset();

    if (caretOffset > 0 &&
       !Character.isJavaIdentifierPart(text.charAt(caretOffset)) &&
       Character.isJavaIdentifierPart(text.charAt(caretOffset - 1))) {
      caretOffset--;
    }

    PsiElement element = file.findElementAt(caretOffset);

    if (element instanceof PsiWhiteSpace && caretOffset > 0) {
      PsiElement anotherElement = file.findElementAt(caretOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    while (element instanceof PsiWhiteSpace) {
      if (element.getNextSibling() == null) {
        element = element.getParent();
      }

      element = element.getNextSibling();
      caretOffset = element.getTextRange().getStartOffset();
    }

    TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    TextRange newRange = null;

    while (element != null && element.getContainingFile() == file) {
      TextRange range = advance(selectionRange, element, text, caretOffset, editor);

      if (range != null &&
         (newRange == null || range.contains(newRange))) {
        newRange = range;
      }

      element = element.getParent();
    }

    if (newRange == null) {
      editor.getSelectionModel().setSelection(caretOffset, caretOffset);
    } else {
      editor.getSelectionModel().setSelection(newRange.getStartOffset(), newRange.getEndOffset());
    }
  }

  private static TextRange advance(TextRange selectionRange,
                                   PsiElement element,
                                   CharSequence text,
                                   int cursorOffset,
                                   Editor editor) {
    TextRange maximum = null;

    for (SelectWordUtil.Selectioner selectioner : SelectWordUtil.SELECTIONERS) {
      if (selectioner.canSelect(element)) {
        List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);

        if (ranges == null) {
          continue;
        }

        for (TextRange range : ranges) {
          if (range == null) {
            continue;
          }

          if (selectionRange.contains(range) && !range.equals(selectionRange)) {
            if (maximum == null || range.contains(maximum)) {
              maximum = range;
            }
          }
        }
      }
    }

    return maximum;
  }
}