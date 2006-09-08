/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 14, 2002
 * Time: 4:06:30 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;

public class CodeBlockUtil {
  private static final int JAVA_BLOCK_BRACE = 1,
    XML_TAG_BRACE = 2,
    JSP_TAG_BRACE = 3,
    UNDEFINED_TAG_BRACE = -1;

  private static int getBraceType(HighlighterIterator iterator) {
    final IElementType type = iterator.getTokenType();
    if (type instanceof IJavaElementType) {
      return JAVA_BLOCK_BRACE;
    }
    else if (type instanceof IXmlLeafElementType) {
      return XML_TAG_BRACE;
    }
    else if (type instanceof IJspElementType) {
      return JSP_TAG_BRACE;
    }
    else {
      return UNDEFINED_TAG_BRACE;
    }
  }

  public static void moveCaretToCodeBlockEnd(Project project, Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    int selectionStart = editor.getSelectionModel().getLeadSelectionOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return;

    int offset = editor.getCaretModel().getOffset();
    final FileType fileType = file.getFileType();
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    int depth = 0;
    int braceType;
    boolean isBeforeLBrace = false;
    if (isLStructuralBrace(fileType, iterator, document.getCharsSequence())) {
      isBeforeLBrace = true;
      depth = -1;
      braceType = getBraceType(iterator);
    } else {
      braceType = UNDEFINED_TAG_BRACE;
    }

    while (true) {
      if (iterator.atEnd()) return;

      if (isRStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
          ( braceType == getBraceType(iterator) ||
            braceType == UNDEFINED_TAG_BRACE
          )
          ) {
        if (depth == 0) break;
        if (braceType == UNDEFINED_TAG_BRACE) {
          braceType = getBraceType(iterator);
        }
        depth--;
      }
      else if (isLStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
               ( braceType == getBraceType(iterator) ||
                 braceType == UNDEFINED_TAG_BRACE
               )
              ) {
        if (braceType == UNDEFINED_TAG_BRACE) {
          braceType = getBraceType(iterator);
        }
        depth++;
      }

      iterator.advance();
    }

    int end = isBeforeLBrace? iterator.getEnd() : iterator.getStart();
    editor.getCaretModel().moveToOffset(end);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (isWithSelection) {
      editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
    } else {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static void moveCaretToCodeBlockStart(Project project, Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    int selectionStart = editor.getSelectionModel().getLeadSelectionOffset();
    if (file == null) return;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return;
    final FileType fileType = file.getFileType();
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    int depth = 0;
    int braceType;
    boolean isAfterRBrace = false;
    if (isRStructuralBrace(fileType, iterator,document.getCharsSequence())) {
      isAfterRBrace = true;
      depth = -1;
      braceType = getBraceType(iterator);
    } else {
      braceType = UNDEFINED_TAG_BRACE;
    }

    while (true) {
      if (iterator.atEnd()) return;

      if (isLStructuralBrace(fileType, iterator, document.getCharsSequence()) &&
          ( braceType == getBraceType(iterator) ||
            braceType == UNDEFINED_TAG_BRACE
          )
          ) {
        if (depth == 0) break;
        if (braceType == UNDEFINED_TAG_BRACE) {
          braceType = getBraceType(iterator);
        }
        depth--;
      }
      else if (isRStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
               ( braceType == getBraceType(iterator) ||
                 braceType == UNDEFINED_TAG_BRACE
               )
              ) {
        if (braceType == UNDEFINED_TAG_BRACE) {
          braceType = getBraceType(iterator);
        }
        depth++;
      }

      iterator.retreat();
    }

    int start = isAfterRBrace ? iterator.getStart() : iterator.getEnd();
    editor.getCaretModel().moveToOffset(start);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (isWithSelection) {
      editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
    } else {
      editor.getSelectionModel().removeSelection();
    }
  }

  private static boolean isLStructuralBrace(final FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    return BraceMatchingUtil.isLBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator,fileText);
  }

  private static boolean isRStructuralBrace(final FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    return BraceMatchingUtil.isRBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator,fileText);
  }
}
