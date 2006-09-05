package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class PasteHandler extends EditorActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.PasteHandler");

  private EditorActionHandler myOriginalHandler;
  private final PasteProvider myPasteReferenceProvider = new CopyReferenceAction.MyPasteProvider();

  public PasteHandler(EditorActionHandler originalAction) {
    myOriginalHandler = originalAction;
  }

  public void execute(final Editor editor, final DataContext dataContext) {
    if (editor.isViewer()) return;

    if (!editor.getDocument().isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), (Project)dataContext.getData(DataConstants.PROJECT))){
        return;
      }
    }

    final Project project = editor.getProject();
    if (project == null || editor.isColumnMode()) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final FileType fileType = file.getFileType();
    /*
    if (fileType == FileType.HTML || fileType == FileType.PLAIN_TEXT) {
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }
    */

    document.startGuardedBlockChecking();
    try {
      if (myPasteReferenceProvider.isPasteEnabled(dataContext)) {
        myPasteReferenceProvider.performPaste(dataContext);
      }
      else {
        doPaste(editor, project, file, fileType, document);
      }
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
    }
    finally {
      document.stopGuardedBlockChecking();
    }
  }

  private static void doPaste(final Editor editor,
                       final Project project,
                       final PsiFile file,
                       final FileType fileType,
                       final Document document) {
    Transferable content = CopyPasteManager.getInstance().getContents();
    if (content != null) {
      String text = null;
      try {
        text = (String)content.getTransferData(DataFlavor.stringFlavor);
      }
      catch (Exception e) {
        editor.getComponent().getToolkit().beep();
      }
      if (text == null) return;

      final CodeInsightSettings settings = CodeInsightSettings.getInstance();

      TextBlockTransferable.ReferenceData[] referenceData = null;
      if (settings.ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
        try {
          referenceData =
          (TextBlockTransferable.ReferenceData[])content.getTransferData(TextBlockTransferable.ReferenceData.FLAVOR);
        }
        catch (UnsupportedFlavorException e) {
        }
        catch (IOException e) {
        }
      }

      if (referenceData != null) { // copy to prevent changing of original by convertLineSeparators
        TextBlockTransferable.ReferenceData[] newReferenceData = new TextBlockTransferable.ReferenceData[referenceData.length];
        for (int i = 0; i < referenceData.length; i++) {
          newReferenceData[i] = (TextBlockTransferable.ReferenceData)referenceData[i].clone();
        }
        referenceData = newReferenceData;
      }

      TextBlockTransferable.FoldingData[] _foldingData = null;
      try {
        _foldingData =
        (TextBlockTransferable.FoldingData[])content.getTransferData(TextBlockTransferable.FoldingData.FLAVOR);
      }
      catch (UnsupportedFlavorException e) {
      }
      catch (IOException e) {
      }

      if (_foldingData != null) { // copy to prevent changing of original by convertLineSeparators
        TextBlockTransferable.FoldingData[] newFoldingData = new TextBlockTransferable.FoldingData[_foldingData.length];
        for (int i = 0; i < _foldingData.length; i++) {
          newFoldingData[i] = (TextBlockTransferable.FoldingData)_foldingData[i].clone();
        }
        _foldingData = newFoldingData;
      }
      final TextBlockTransferable.FoldingData[] foldingData = _foldingData;

      text = TextBlockTransferable.convertLineSeparators(text, "\n", referenceData, foldingData);

      final int col = editor.getCaretModel().getLogicalPosition().column;
      if (editor.getSelectionModel().hasSelection()) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              EditorModificationUtil.deleteSelectedText(editor);
            }
          }
        );
      }

      RawText rawText = null;
      try {
        rawText = (RawText)content.getTransferData(RawText.FLAVOR);
      }
      catch (UnsupportedFlavorException e) {
      }
      catch (IOException e) {
      }

      String newText = escapeIfStringLiteral(project, file, editor, text, rawText);
      int indentOptions = text.equals(newText) ? settings.REFORMAT_ON_PASTE : CodeInsightSettings.REFORMAT_BLOCK;
      text = newText;

      if (fileType != StdFileTypes.XML && fileType != StdFileTypes.JAVA && indentOptions != CodeInsightSettings.NO_REFORMAT) {
        indentOptions = CodeInsightSettings.INDENT_BLOCK;
      }

      int length = text.length();
      final String text1 = text;
      final int offset = editor.getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            EditorModificationUtil.insertStringAtCaret(editor, text1, false, false);
          }
        }
      );

      final RangeMarker bounds = document.createRangeMarker(offset, offset + length);

      editor.getCaretModel().moveToOffset(bounds.getEndOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();

      if (foldingData != null && foldingData.length > 0) {
        final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
        foldingManager.updateFoldRegions(editor);

        Runnable processor1 = new Runnable() {
          public void run() {
            for (TextBlockTransferable.FoldingData data : foldingData) {
              FoldRegion region = foldingManager.findFoldRegion(editor, data.startOffset + offset, data.endOffset + offset);
              if (region != null) {
                region.setExpanded(data.isExpanded);
              }
            }
          }
        };
        editor.getFoldingModel().runBatchFoldingOperation(processor1);
      }

      if (referenceData != null && referenceData.length > 0) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final TextBlockTransferable.ReferenceData[] referenceData1 = referenceData;
        final PsiJavaCodeReferenceElement[] refs = findReferencesToRestore(file, bounds, referenceData1);
        if (settings.ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
          askReferencesToRestore(project, refs, referenceData1);
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            restoreReferences(referenceData1, refs);
          }
        });
      }

      final int indentOptions1 = indentOptions;
      ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            switch (indentOptions1) {
              case CodeInsightSettings.INDENT_BLOCK:
                indentBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset(), col);
                break;

              case CodeInsightSettings.INDENT_EACH_LINE:
                indentEachLine(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
                break;

              case CodeInsightSettings.REFORMAT_BLOCK:
                indentEachLine(project, editor, bounds.getStartOffset(), bounds.getEndOffset()); // this is needed for example when inserting a comment before method
                reformatBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
                break;
            }
          }
        }
      );

      if (bounds.isValid()) {
        editor.getCaretModel().moveToOffset(bounds.getEndOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().removeSelection();
        editor.putUserData(EditorEx.LAST_PASTED_REGION, new TextRange(bounds.getStartOffset(), bounds.getEndOffset()));
      }
    }
  }

  private static String escapeIfStringLiteral(final Project project,
                                              final PsiFile file,
                                              final Editor editor,
                                              String text, final RawText rawText) {
  //  if ("\n".equals(text)) return text;
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(caretOffset);
    if (elementAtCaret instanceof PsiJavaToken &&
        ((PsiJavaToken)elementAtCaret).getTokenType() == JavaTokenType.STRING_LITERAL &&
        caretOffset > elementAtCaret.getTextOffset()) {
      if (rawText != null && rawText.rawText != null) return rawText.rawText; // Copied from the string literal. Copy as is.

      StringBuffer buffer = new StringBuffer(text.length());
      CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
      @NonNls String breaker = codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE ? "\\n\"\n+ \"" : "\\n\" +\n\"";
      final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        buffer.append(StringUtil.escapeStringCharacters(line));
        if (i != lines.length - 1) buffer.append(breaker);
      }
      text = buffer.toString();
    }
    return text;
  }

  private static PsiJavaCodeReferenceElement[] findReferencesToRestore(PsiFile file,
                                                                       RangeMarker bounds,
                                                                       TextBlockTransferable.ReferenceData[] referenceData) {
    PsiManager manager = file.getManager();
    PsiResolveHelper helper = manager.getResolveHelper();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referenceData.length];
    for (int i = 0; i < referenceData.length; i++) {
      TextBlockTransferable.ReferenceData data = referenceData[i];

      PsiClass refClass = manager.findClass(data.qClassName, file.getResolveScope());
      if (refClass == null) continue;

      int startOffset = data.startOffset + bounds.getStartOffset();
      int endOffset = data.endOffset + bounds.getStartOffset();
      PsiElement element = file.findElementAt(startOffset);

      if (element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)element.getParent();
        TextRange range = reference.getTextRange();
        if (range.getStartOffset() == startOffset && range.getEndOffset() == endOffset) {
          if (data.staticMemberName == null) {
            PsiClass refClass1 = helper.resolveReferencedClass(reference.getText(), reference);
            if (refClass1 == null || !manager.areElementsEquivalent(refClass, refClass1)) {
              refs[i] = reference;
            }
          } else {
            if (reference instanceof PsiReferenceExpression) {
              PsiElement referent = reference.resolve();
              if (!(referent instanceof PsiNamedElement) || !data.staticMemberName.equals(((PsiNamedElement)referent).getName())
                  || !(referent instanceof PsiMember) || !data.qClassName.equals(((PsiMember)referent).getContainingClass().getQualifiedName())) {
                refs[i] = reference;
              }
            }
          }
        }
      }
    }
    return refs;
  }

  private static void restoreReferences(TextBlockTransferable.ReferenceData[] referenceData,
                                        PsiJavaCodeReferenceElement[] refs) {
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement reference = refs[i];
      if (reference != null) {
        try {
          PsiManager manager = reference.getManager();
          TextBlockTransferable.ReferenceData refData = referenceData[i];
          PsiClass refClass = manager.findClass(refData.qClassName, reference.getResolveScope());
          if (refClass != null) {
            if (refData.staticMemberName == null) {
              reference.bindToElement(refClass);
            }
            else {
              LOG.assertTrue(reference instanceof PsiReferenceExpression);
              ((PsiReferenceExpression)reference).bindToElementViaStaticImport(refClass);
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private static void indentBlock(Project project, Editor editor, int startOffset, int endOffset, int originalCaretCol) {
    Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    boolean hasNewLine = false;

    for (int i = endOffset - 1; i >= startOffset; i--) {
      char c = chars.charAt(i);
      if (c == '\n' || c == '\r') {
        hasNewLine = true;
        break;
      }
      if (c != ' ' && c != '\t') return; // do not indent if does not end with line separator
    }

    if (!hasNewLine) return;
    int lineStart = CharArrayUtil.shiftBackwardUntil(chars, startOffset - 1, "\n\r") + 1;
    int spaceEnd = CharArrayUtil.shiftForward(chars, lineStart, " \t");
    if (startOffset <= spaceEnd) { // we are in starting spaces
      if (lineStart != startOffset) {
        String deletedS = chars.subSequence(lineStart, startOffset).toString();
        document.deleteString(lineStart, startOffset);
        startOffset = lineStart;
        endOffset -= deletedS.length();
        document.insertString(endOffset, deletedS);
        LogicalPosition pos = new LogicalPosition(editor.getCaretModel().getLogicalPosition().line, originalCaretCol);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file.getLanguage().getEffectiveFormattingModelBuilder(file) != null) {
        adjustBlockIndent(project, document, file, startOffset, endOffset);
      }
      else {
        indentPlainTextBlock(document, startOffset, endOffset, originalCaretCol);
      }
    }
  }

  private static void indentEachLine(Project project, Editor editor, int startOffset, int endOffset) {
    Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    endOffset = CharArrayUtil.shiftBackward(chars, endOffset - 1, "\n\r") + 1;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    try {
      codeStyleManager.adjustLineIndent(file, new TextRange(startOffset, endOffset));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void reformatBlock(Project project, Editor editor, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    try {
      CodeStyleManager.getInstance(project).reformatRange(file, startOffset, endOffset, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void adjustBlockIndent(Project project, Document document, PsiFile file, int startOffset, int endOffset) {
    final FileType fileType = file.getFileType();
    indentJavaBlock(project, document, startOffset, endOffset, file, fileType);
  }

  private static void indentPlainTextBlock(Document document, int startOffset, int endOffset, int originalCaretColumn) {
    CharSequence chars = document.getCharsSequence();
    int spaceEnd = CharArrayUtil.shiftForward(chars, startOffset, " \t");
    if (spaceEnd > endOffset) return;
    int indentLevel = originalCaretColumn;
    if (indentLevel == 0) return;

    char[] fill = new char[indentLevel];
    Arrays.fill(fill, ' ');
    String indentString = new String(fill);

    int offset = spaceEnd;
    while (true) {
      document.insertString(offset, indentString);
      chars = document.getCharsSequence();
      endOffset += indentLevel;
      offset = CharArrayUtil.shiftForwardUntil(chars, offset, "\n") + 1;
      if (offset >= endOffset || offset >= document.getTextLength()) break;
    }
  }

  private static void indentJavaBlock(Project project, Document document,
                                      int startOffset,
                                      int endOffset,
                                      PsiFile file,
                                      final FileType fileType) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    CharSequence chars = document.getCharsSequence();
    int spaceStart = CharArrayUtil.shiftBackwardUntil(chars, startOffset - 1, "\n\r") + 1;
    int spaceEnd = CharArrayUtil.shiftForward(chars, startOffset, " \t");
    if (spaceEnd > endOffset) return;
    while (!codeStyleManager.isLineToBeIndented(file, spaceEnd)) {
      spaceStart = CharArrayUtil.shiftForwardUntil(chars, spaceEnd, "\n\r");
      spaceStart = CharArrayUtil.shiftForward(chars, spaceStart, "\n\r");
      spaceEnd = CharArrayUtil.shiftForward(chars, spaceStart, " \t");
      if (spaceEnd >= endOffset) return;
    }

    Indent indent = codeStyleManager.getIndent(chars.subSequence(spaceStart, spaceEnd).toString(), fileType);
    int newEnd = codeStyleManager.adjustLineIndent(document, spaceEnd);
    chars = document.getCharsSequence();
    if (spaceStart > newEnd) {
      newEnd = spaceStart; //TODO lesya. Try to reproduce it. SCR52139
    }
    Indent newIndent = codeStyleManager.getIndent(chars.subSequence(spaceStart, newEnd).toString(), fileType);
    Indent indentShift = newIndent.subtract(indent);
    ArrayList<Boolean> indentFlags = new ArrayList<Boolean>();
    if (!indentShift.isZero()) {
      //System.out.println("(paste block) old indent = " + indent);
      //System.out.println("(paste block) new indent = " + newIndent);
      endOffset += newEnd - spaceEnd;

      int offset = newEnd;
      while (true) {
        offset = CharArrayUtil.shiftForwardUntil(chars, offset, "\n\r");
        if (offset >= endOffset) break;
        offset = CharArrayUtil.shiftForward(chars, offset, "\n\r");
        if (offset >= endOffset) break;
        int offset1 = CharArrayUtil.shiftForward(chars, offset, " \t");
        if (offset1 >= endOffset) break;
        if (chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r') { // empty line
          offset = offset1;
          continue;
        }
        Boolean flag = codeStyleManager.isLineToBeIndented(file, offset) ? Boolean.TRUE : Boolean.FALSE;
        indentFlags.add(flag);
        offset = offset1;
      }

      offset = newEnd;
      int count = 0;
      while (true) {
        offset = CharArrayUtil.shiftForwardUntil(chars, offset, "\n\r");
        if (offset >= endOffset) break;
        offset = CharArrayUtil.shiftForward(chars, offset, "\n\r");
        if (offset >= endOffset) break;
        int offset1 = CharArrayUtil.shiftForward(chars, offset, " \t");
        if (offset1 >= endOffset) break;
        if (chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r') { // empty line
          offset = offset1;
          continue;
        }
        int index = count++;
        if (indentFlags.get(index) == Boolean.FALSE) {
          offset = offset1;
          continue;
        }
        String space = chars.subSequence(offset, offset1).toString();
        indent = codeStyleManager.getIndent(space, fileType);
        indent = indent.add(indentShift);
        String newSpace = codeStyleManager.fillIndent(indent, fileType);
        document.replaceString(offset, offset1, newSpace);
        chars = document.getCharsSequence();
        offset += newSpace.length();
        endOffset += newSpace.length() - space.length();
      }
    }
  }

  private static void askReferencesToRestore(Project project, PsiJavaCodeReferenceElement[] refs,
                                      TextBlockTransferable.ReferenceData[] referenceData) {
    PsiManager manager = PsiManager.getInstance(project);

    ArrayList<Object> array = new ArrayList<Object>();
    Object[] refObjects = new Object[refs.length];
    for (int i = 0; i < referenceData.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref != null) {
        LOG.assertTrue(ref.isValid());
        TextBlockTransferable.ReferenceData data = referenceData[i];
        PsiClass refClass = manager.findClass(data.qClassName, ref.getResolveScope());
        if (refClass == null) continue;

        Object refObject = refClass;
        if (data.staticMemberName != null) {
          //Show static members as Strings
          refObject = refClass.getQualifiedName() + "." + data.staticMemberName;
        }
        refObjects[i] = refObject;

        if (!array.contains(refObject)) {
          array.add(refObject);
        }
      }
    }
    if (array.size() == 0) return;

    Object[] selectedObjects = array.toArray(new Object[array.size()]);
    Arrays.sort(
      selectedObjects,
      new Comparator<Object>() {
        public int compare(Object o1, Object o2) {
          String fqName1 = getFQName(o1);
          String fqName2 = getFQName(o2);
          return fqName1.compareToIgnoreCase(fqName2);
        }
      }
    );

    RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, selectedObjects);
    dialog.show();
    selectedObjects = dialog.getSelectedElements();

    for (int i = 0; i < referenceData.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref != null) {
        LOG.assertTrue(ref.isValid());
        Object refObject = refObjects[i];
        boolean found = false;
        for (Object selected : selectedObjects) {
          if (refObject.equals(selected)) {
            found = true;
            break;
          }
        }
        if (!found) {
          refs[i] = null;
        }
      }
    }
  }

  private static String getFQName(Object element) {
    return element instanceof PsiClass ? ((PsiClass)element).getQualifiedName() : (String)element;
  }
}