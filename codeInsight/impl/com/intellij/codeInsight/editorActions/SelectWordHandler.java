package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtil;

import java.util.List;

public class SelectWordHandler extends EditorActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.SelectWordHandler");

  private EditorActionHandler myOriginalHandler;

  public SelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: execute(editor='" + editor + "')");
    }

    Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(DataConstants.PROJECT);
    if (project == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

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
    CharSequence text = editor.getDocument().getCharsSequence();

    if (file instanceof PsiCompiledElement) {
      file = (PsiFile)((PsiCompiledElement)file).getMirror();
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.select.word");

    int caretOffset = editor.getCaretModel().getOffset();

    if (caretOffset > 0 && caretOffset < editor.getDocument().getTextLength() &&
        !Character.isJavaIdentifierPart(text.charAt(caretOffset)) && Character.isJavaIdentifierPart(text.charAt(caretOffset - 1))) {
      caretOffset--;
    }

    PsiElement element = findElementAt(file, caretOffset);

    if (element instanceof PsiWhiteSpace && caretOffset > 0) {
      PsiElement anotherElement = findElementAt(file, caretOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    while (element instanceof PsiWhiteSpace) {
      nextParent:
      while (element.getNextSibling() == null) {
        final PsiElement parent = element.getParent();
        final PsiElement[] children = parent.getChildren();

        if (children[children.length - 1] == element) {
          element = parent;
        }
        else {
          element = parent;
          break nextParent;
        }
      }

      element = element.getNextSibling();
      if (element == null) return;
      caretOffset = element.getTextRange().getStartOffset();
    }

    TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    TextRange newRange = null;

    int textLength = editor.getDocument().getTextLength();

    while (element != null && !(element instanceof PsiFile)) {
      newRange = advance(selectionRange, element, text, caretOffset, editor);

      if (newRange != null) {
        break;
      }

      element = getUpperElement(element, selectionRange);
    }

    if (newRange == null) {
      newRange = new TextRange(0, textLength);
    }

    if (!editor.getSelectionModel().hasSelection() || newRange.contains(selectionRange)) {
      selectionRange = newRange;
    }

    editor.getSelectionModel().setSelection(selectionRange.getStartOffset(), selectionRange.getEndOffset());
  }

  private static PsiElement findElementAt(final PsiFile file, final int caretOffset) {
    return (CodeInsightUtil.isAntFile(file))
           ? file.getViewProvider().findElementAt(caretOffset, file.getLanguage())
           : file.findElementAt(caretOffset);
  }

  private static PsiElement getUpperElement(final PsiElement e, final TextRange selectionRange) {
    final PsiElement parent = e.getParent();

    if (PsiUtil.isInJspFile(e.getContainingFile()) && e.getLanguage()instanceof JavaLanguage) {
      final JspFile psiFile = PsiUtil.getJspFile(e.getContainingFile());
      if (e.getParent().getTextLength() == psiFile.getTextLength()) {
        PsiFile baseRoot = psiFile.getBaseLanguageRoot();
        PsiElement elt = baseRoot.getNode().findLeafElementAt(e.getTextRange().getStartOffset()).getPsi();
        return elt;
      }
    }

    return parent;
  }

  private static TextRange advance(TextRange selectionRange, PsiElement element, CharSequence text, int cursorOffset, Editor editor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: advance(selectionRange='" + selectionRange + "', element='" + element + "')");
    }
    TextRange minimumRange = null;

    for (SelectWordUtil.Selectioner selectioner : SelectWordUtil.SELECTIONERS) {
      if (!selectioner.canSelect(element)) {
        continue;
      }
      List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);

      if (ranges == null) {
        continue;
      }

      for (TextRange range : ranges) {
        if (range == null) {
          continue;
        }

        if (range.contains(selectionRange) && !range.equals(selectionRange)) {
          if (minimumRange == null || minimumRange.contains(range)) {
            minimumRange = range;
          }
        }
      }
    }

    //TODO remove assert
    if (minimumRange != null) {
      LOG.assertTrue(minimumRange.getEndOffset() <= text.length());
    }

    return minimumRange;
  }
}