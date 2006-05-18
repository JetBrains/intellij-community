package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorDelegate;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * A TestCase for single PsiFile being opened in Editor conversion. See configureXXX and checkResultXXX method docs.
 */
public class LightCodeInsightTestCase extends LightIdeaTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.LightCodeInsightTestCase");

  protected Editor myEditor;
  protected PsiFile myFile;
  protected VirtualFile myVFile;

  private static final String CARET_MARKER = "<caret>";
  private static final String SELECTION_START_MARKER = "<selection>";
  private static final String SELECTION_END_MARKER = "</selection>";

  protected void runTest() throws Throwable {
    final Throwable[] throwable = new Throwable[] {null};
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {

            try {
              LightCodeInsightTestCase.super.runTest();
            } catch (Throwable t) {
              throwable[0] = t;
            }
          }
        }, "", null);
      }
    });

    if (throwable[0] != null) {
      throw throwable[0];
    }
  }

  /**
   * Configure test from data file. Data file is usual java, xml or whatever file that needs to be tested except it
   * has &lt;caret&gt; marker where caret should be placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt;
   * denoting selection bounds.
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @throws Exception
   */
  protected void configureByFile(@NonNls String filePath) throws Exception {
    String fullPath = getTestDataPath() + filePath;

    final File ioFile = new File(fullPath);
    String fileText = new String(FileUtil.loadFileText(ioFile));
    fileText = StringUtil.convertLineSeparators(fileText, "\n");

    configureFromFileText(ioFile.getName(), fileText);
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  /**
   * Same as configureByFile but text is provided directly.
   * @param fileName - name of the file.
   * @param fileText - data file text.
   * @throws IOException
   */
  protected void configureFromFileText(final String fileName, String fileText) throws IOException {
    final Document fakeDocument = new DocumentImpl(fileText);

    int caretIndex = fileText.indexOf(CARET_MARKER);
    int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? fakeDocument.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0 ? fakeDocument.createRangeMarker(selStartIndex, selStartIndex) : null;
    final RangeMarker selEndMarker = selEndIndex >= 0 ? fakeDocument.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      fakeDocument.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      fakeDocument.deleteString(selStartMarker.getStartOffset(),
                                selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      fakeDocument.deleteString(selEndMarker.getStartOffset(),
                                selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    String newFileText = fakeDocument.getText();
    setupFileEditorAndDocument(fileName, newFileText);
    setupCaret(caretMarker, newFileText);
    setupSelection(selStartMarker, selEndMarker);
    setupEditorForInjectedLangugae();
  }

  private void setupSelection(final RangeMarker selStartMarker, final RangeMarker selEndMarker) {
    if (selStartMarker != null) {
      myEditor.getSelectionModel().setSelection(selStartMarker.getStartOffset(), selEndMarker.getStartOffset());
    }
  }

  private void setupCaret(final RangeMarker caretMarker, String fileText) {
    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(fileText, caretMarker.getStartOffset());
      int caretCol = EditorUtil.calcColumnNumber(null, myEditor.getDocument().getText(),
                                                 myEditor.getDocument().getLineStartOffset(caretLine), caretMarker.getStartOffset(),
                                                 CodeStyleSettingsManager.getSettings(getProject()).JAVA_INDENT_OPTIONS.TAB_SIZE);
      LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }
  }

  private static Editor createEditor(VirtualFile file) {
    return FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
  }

  private void setupFileEditorAndDocument(final String fileName, String fileText) throws IOException {
    deleteVFile();
    myVFile = getSourceRoot().createChildData(this, fileName);
    VfsUtil.saveText(myVFile, fileText);
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    manager.reloadFromDisk(manager.getDocument(myVFile));
    myFile = getPsiManager().findFile(myVFile);
    assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", myFile);
    assertTrue(myFile.isPhysical());
    myEditor = createEditor(myVFile);
  }

  private void setupEditorForInjectedLangugae() {
    Editor editor = FileEditorManagerImpl.getEditorForInjectedLanguage(myEditor, myFile);
    if (editor instanceof EditorDelegate) {
      myFile = ((EditorDelegate)editor).getInjectedFile();
      myEditor = editor;
    }
  }

  private void deleteVFile() {
    if (myVFile != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            myVFile.delete(this);
          } catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  protected void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }
    deleteVFile();
    myEditor = null;
    myFile = null;
    myVFile = null;
    super.tearDown();
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @throws Exception
   */
  protected void checkResultByFile(@NonNls String filePath) throws Exception {
    checkResultByFile(null, filePath, false);
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   * @throws Exception
   */
  protected void checkResultByFile(String message, final String filePath, final boolean ignoreTrailingSpaces) throws Exception {
    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (ignoreTrailingSpaces) {
      ((DocumentEx) myEditor.getDocument()).stripTrailingSpaces(false);
    }

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String fullPath = getTestDataPath() + filePath;

    File ioFile = new File(fullPath);

    assertTrue(getMessage("Cannot find file " + fullPath, message), ioFile.exists());
    String fileText = null;
    try {
      fileText = new String(FileUtil.loadFileText(ioFile));
    } catch (IOException e) {
      LOG.error(e);
    }
    checkResultByText(message, StringUtil.convertLineSeparators(fileText, "\n"), ignoreTrailingSpaces);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   * @param fileText
   */
  protected void checkResultByText(@NonNls String fileText) {
    checkResultByText(null, fileText, false);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param fileText
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByText(String message, String fileText, final boolean ignoreTrailingSpaces) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    });
    final Document document = EditorFactory.getInstance().createDocument(fileText);

    int caretIndex = fileText.indexOf(CARET_MARKER);
    int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0
                                       ? document.createRangeMarker(selStartIndex, selStartIndex)
                                       : null;
    final RangeMarker selEndMarker = selEndIndex >= 0
                                     ? document.createRangeMarker(selEndIndex, selEndIndex)
                                     : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(),
                            selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(),
                            selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    String newFileText = document.getText();
    String newFileText1 = newFileText;
    if (ignoreTrailingSpaces) {
      Document document1 = EditorFactory.getInstance().createDocument(newFileText);
      ((DocumentEx) document1).stripTrailingSpaces(false);
      newFileText1 = document1.getText();
    }

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    String text = myFile.getText();
    assertEquals(getMessage("Text mismatch", message), newFileText1, text);

    checkCaretPosition(caretMarker, newFileText, message);
    checkSelection(selStartMarker, selEndMarker, newFileText, message);
  }

  private static String getMessage(String engineMessage, String userMessage) {
    if (userMessage == null) return engineMessage;
    StringBuffer buf = new StringBuffer(userMessage);
    buf.append(" [").append(engineMessage).append("]");
    return buf.toString();
  }

  private void checkSelection(final RangeMarker selStartMarker, final RangeMarker selEndMarker, String newFileText, String message) {
    if (selStartMarker != null && selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(newFileText, selStartMarker.getStartOffset());
      int selStartCol = selStartMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(newFileText, selEndMarker.getEndOffset());
      int selEndCol = selEndMarker.getEndOffset() - StringUtil.lineColToOffset(newFileText, selEndLine, 0);

      assertEquals(
          getMessage("selectionStartLine", message),
          selStartLine + 1,
          StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      assertEquals(
          getMessage("selectionStartCol", message),
          selStartCol + 1,
          myEditor.getSelectionModel().getSelectionStart() -
          StringUtil.lineColToOffset(newFileText, selStartLine, 0) +
                                                                   1);

      assertEquals(
          getMessage("selectionEndLine", message),
          selEndLine + 1,
          StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      assertEquals(
          getMessage("selectionEndCol", message),
          selEndCol + 1,
          myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(newFileText, selEndLine, 0) +
          1);
    } else {
      assertTrue(getMessage("must not have selection", message), !myEditor.getSelectionModel().hasSelection());
    }
  }

  private void checkCaretPosition(final RangeMarker caretMarker, String newFileText, String message) {
    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newFileText, caretMarker.getStartOffset());
      //int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, caretLine, 0);
      int caretCol = EditorUtil.calcColumnNumber(null, newFileText,
                                                 StringUtil.lineColToOffset(newFileText, caretLine, 0),
                                                 caretMarker.getStartOffset(),
                                                 CodeStyleSettingsManager.getSettings(getProject()).JAVA_INDENT_OPTIONS.TAB_SIZE);

      assertEquals(getMessage("caretLine", message), caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      assertEquals(getMessage("caretColumn", message), caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.EDITOR)) {
      return myEditor;
    }
    else if (dataId.equals(DataConstants.PSI_FILE)) {
      return myFile;
    }
    else {
      return super.getData(dataId);
    }
  }

  /**
   * @return Editor used in test.
   */
  protected Editor getEditor() {
    return myEditor;
  }

  /**
   * @return PsiFile opened in editor used in test
   */
  protected PsiFile getFile() {
    return myFile;
  }

  protected VirtualFile getVFile() {
    return myVFile;
  }
}
