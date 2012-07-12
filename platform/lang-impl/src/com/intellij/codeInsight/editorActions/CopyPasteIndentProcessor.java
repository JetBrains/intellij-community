package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class CopyPasteIndentProcessor implements CopyPastePostProcessor<IndentTransferableData> {
  @Override
  public IndentTransferableData collectTransferableData(PsiFile file,
                                                          Editor editor,
                                                          int[] startOffsets,
                                                          int[] endOffsets) {
    if (!acceptFileType(file.getFileType())) {
      return null;
    }

    if (startOffsets.length != 1) {
      return null;
    }
    Document document = editor.getDocument();
    int selStartLine = document.getLineNumber(startOffsets[0]);
    // check that selection starts at or before the first non-whitespace character on a line
    for (int offset = startOffsets[0] - 1; offset >= document.getLineStartOffset(selStartLine); offset--) {
      if (!Character.isWhitespace(document.getCharsSequence().charAt(offset))) {
        return null;
      }
    }
    int tabSize = CodeStyleFacade.getInstance(file.getProject()).getTabSize(file.getFileType());
    int start = document.getLineStartOffset(selStartLine);
    int end = document.getLineEndOffset(selStartLine);
    int minIndent = getIndent(document.getCharsSequence(), start, end, tabSize);

    int firstNonSpaceChar = CharArrayUtil.shiftForward(document.getCharsSequence(), startOffsets[0], " \t");
    int firstLineLeadingSpaces = (firstNonSpaceChar <= document.getLineEndOffset(selStartLine)) ? firstNonSpaceChar - startOffsets[0] : 0;
    return new IndentTransferableData(minIndent, 0, firstLineLeadingSpaces);
  }

  private static boolean acceptFileType(FileType fileType) {
    for(PreserveIndentOnPasteBean bean: Extensions.getExtensions(PreserveIndentOnPasteBean.EP_NAME)) {
      if (fileType.getName().equals(bean.fileType)) {
        return true;
      }
    }
    return false;
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
        final Object transferData = content.getTransferData(flavor);
        if (transferData instanceof IndentTransferableData) {
          indentData = (IndentTransferableData)transferData;
        }
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
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE) {
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null || !acceptFileType(psiFile.getFileType())) {
      return;
    }
    //System.out.println("--- before indent ---\n" + document.getText());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String pastedText = document.getText(TextRange.create(bounds));

        int startLine = document.getLineNumber(bounds.getStartOffset());
        int endLine = document.getLineNumber(bounds.getEndOffset());

        // don't indent single-line text
        if (!StringUtil.startsWithWhitespace(pastedText) && !StringUtil.endsWithLineBreak(pastedText) &&
             !(StringUtil.splitByLines(pastedText).length > 1))
          return;

        int startLineStart = document.getLineStartOffset(startLine);
        // don't indent first line if there's any text before it
        final String textBeforeFirstLine = document.getText(new TextRange(startLineStart, bounds.getStartOffset()));

        //insert on top level, doesn't need indent
        if (caretColumn == 0 && !pastedText.startsWith(" "))
          return;

        if (textBeforeFirstLine.trim().length() == 0) {
          EditorActionUtil.indentLine(project, editor, startLine, -value.getFirstLineLeadingSpaces());
        }

        final int caretOffset = editor.getCaretModel().getOffset();
        int realCaretColumn = caretOffset - document.getLineStartOffset(document.getLineNumber(caretOffset));

        final List<String> strings = StringUtil.split(pastedText, "\n");
        if (realCaretColumn >= value.getIndent() && !strings.isEmpty() &&
            StringUtil.isEmptyOrSpaces(strings.get(strings.size()-1))) endLine -=1;

        for (int i = startLine+1; i <= endLine; i++) {
          int indent = realCaretColumn - value.getIndent();
          EditorActionUtil.indentLine(project, editor, i, indent);
        }
        indented.set(Boolean.TRUE);
      }
    });
    //System.out.println("--- after indent ---\n" + document.getText());
  }
}
