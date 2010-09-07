package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.IndentTransferableData;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * @author yole
 */
public class CopyPasteIndentProcessor implements CopyPastePostProcessor<IndentTransferableData> {
  @Override
  public IndentTransferableData collectTransferableData(PsiFile file,
                                                          Editor editor,
                                                          int[] startOffsets,
                                                          int[] endOffsets) {
    if (startOffsets.length != 1) {
      return null;
    }
    Document document = editor.getDocument();
    int selStartLine = document.getLineNumber(startOffsets [0]);
    int selEndLine = document.getLineNumber(endOffsets [0]);
    if (selStartLine == selEndLine) {
      return null;
    }
    // check that selection starts at or before the first non-whitespace character on a line
    for (int offset = startOffsets[0] - 1; offset >= document.getLineStartOffset(selStartLine); offset--) {
      if (!Character.isWhitespace(document.getCharsSequence().charAt(offset))) {
        return null;
      }
    }
    int minIndent = Integer.MAX_VALUE;
    int tabSize = CodeStyleFacade.getInstance(file.getProject()).getTabSize(file.getFileType());
    for (int line = selStartLine; line <= selEndLine; line++) {
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);
      int indent = getIndent(document.getCharsSequence(), start, end, tabSize);
      if (indent >= 0) {
        minIndent = Math.min(minIndent, indent);
      }
    }
    int firstNonSpaceChar = CharArrayUtil.shiftForward(document.getCharsSequence(), startOffsets [0], " \t");
    int firstLineLeadingSpaces = (firstNonSpaceChar <= document.getLineEndOffset(selStartLine)) ? firstNonSpaceChar - startOffsets[0] : 0;
    return new IndentTransferableData(minIndent, firstLineLeadingSpaces);
  }

  private static int getIndent(CharSequence chars, int start, int end, int tabSize) {
    int result = 0;
    boolean nonEmpty = false;
    for(int i=start; i<end; i++)  {
      if (chars.charAt(i) == ' ') {
        result++;
      }
      else if (chars.charAt(i) == '\t') {
        result = ((result / tabSize) + 1) * tabSize;
      }
      else {
        nonEmpty = true;
        break;
      }
    }
    return nonEmpty ? result : -1;
  }

  @Override
  public IndentTransferableData extractTransferableData(Transferable content) {
    IndentTransferableData indentData = null;
    try {
      final DataFlavor flavor = IndentTransferableData.getDataFlavorStatic();
      if (flavor != null) {
        indentData = (IndentTransferableData)content.getTransferData(flavor);
      }
    }
    catch (UnsupportedFlavorException e) {
      // do nothing
    }
    catch (IOException e) {
      // do nothing
    }
    return indentData;
  }

  @Override
  public void processTransferableData(final Project project,
                                      final Editor editor,
                                      final RangeMarker bounds,
                                      final int caretColumn,
                                      final Ref<Boolean> indented,
                                      final IndentTransferableData value) {
    if (value.getIndent() > 0) {
      final Document document = editor.getDocument();
      //System.out.println("--- before indent ---\n" + document.getText());
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          int startLine = document.getLineNumber(bounds.getStartOffset());
          int endLine = document.getLineNumber(bounds.getEndOffset());
          int startLineStart = document.getLineStartOffset(startLine);
          // don't indent first line if there's any text before it
          final String textBeforeFirstLine = document.getText(new TextRange(startLineStart, bounds.getStartOffset()));
          if (textBeforeFirstLine.trim().length() == 0) {
            EditorActionUtil.indentLine(project, editor, startLine, -value.getFirstLineLeadingSpaces());
          }
          for (int i = startLine+1; i <= endLine; i++) {
            EditorActionUtil.indentLine(project, editor, i, caretColumn - value.getIndent());
          }
          indented.set(Boolean.TRUE);
        }
      });
      //System.out.println("--- after indent ---\n" + document.getText());
    }
  }
}
