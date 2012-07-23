package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

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
    if (!acceptFileType(file.getFileType())) {
      return null;
    }
    return new IndentTransferableData(editor.getCaretModel().getOffset());
  }

  private static boolean acceptFileType(FileType fileType) {
    for(PreserveIndentOnPasteBean bean: Extensions.getExtensions(PreserveIndentOnPasteBean.EP_NAME)) {
      if (fileType.getName().equals(bean.fileType)) {
        return true;
      }
    }
    return false;
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
                                      final int caretOffset,
                                      final Ref<Boolean> indented,
                                      final IndentTransferableData value) {
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE) {
      return;
    }
    if (value.getOffset() == editor.getCaretModel().getOffset()) return;

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

        //calculate from indent
        int fromIndent = StringUtil.findFirst(pastedText, CharFilter.NOT_WHITESPACE_FILTER);
        if (fromIndent < 0) fromIndent = 0;

        //calculate to indent
        String initialText = document.getText(TextRange.create(0, bounds.getStartOffset())) +
                   document.getText(TextRange.create(bounds.getEndOffset(), document.getTextLength()));
        final DocumentImpl initialDocument = new DocumentImpl(initialText);
        final int lineNumber = initialDocument.getLineNumber(caretOffset);
        final int offset = getLineStartSafeOffset(initialDocument, lineNumber);
        final int caretColumn = caretOffset - offset;

        String toString = initialDocument.getText(TextRange.create(offset, initialDocument.getLineEndOffset(lineNumber)));
        int toIndent = StringUtil.findFirst(toString, new CharFilter() {
          @Override
          public boolean accept(char ch) {
            return ch != ' ';
          }
        });
        if (toIndent < 0 || toString.startsWith("\n")) {
          toIndent = caretColumn;
        }

        // actual difference in indentation level
        int indent = toIndent - fromIndent;

        // don't indent single-line text
        if (!StringUtil.startsWithWhitespace(pastedText) && !StringUtil.endsWithLineBreak(pastedText) &&
             !(StringUtil.splitByLines(pastedText).length > 1))
          return;

        if (pastedText.endsWith("\n")) endLine -= 1;

        for (int i = startLine; i <= endLine; i++) {
          EditorActionUtil.indentLine(project, editor, i, indent);
        }
        indented.set(Boolean.TRUE);
      }
    });
    //System.out.println("--- after indent ---\n" + document.getText());
  }

  public static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }

}
