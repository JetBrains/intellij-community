package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestData;
import com.intellij.util.Function;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Mike
 */
public abstract class CodeInsightTestCase extends PsiTestCase {
  protected Editor myEditor;

  public CodeInsightTestCase() {
    myRunCommandForTest = true;
  }

  protected Editor createEditor(VirtualFile file) {
    final FileEditorManager instance = FileEditorManager.getInstance(myProject);

    if (file.getFileType().isBinary()) {
      return null;
    }

    return instance.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
  }

  protected void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }
    myEditor = null;
    super.tearDown();
  }

  protected PsiTestData createData() {
    return new CodeInsightTestData();
  }

  public static final String CARET_MARKER = "<caret>";
  public static final String SELECTION_START_MARKER = "<selection>";
  public static final String SELECTION_END_MARKER = "</selection>";

  protected void configureByFile(String filePath) throws Exception {
    configureByFile(filePath, null);
  }
  protected void configureByFiles(String projectRoot,String... files) throws Exception {
    final VirtualFile[] vFiles = new VirtualFile[files.length];
    for (int i = 0; i < files.length; i++) {
      String path = files[i];
      final String fullPath = FileUtil.toSystemIndependentName(getTestDataPath() + path);
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath);
      vFiles[i] = vFile;
      assertNotNull("file " + fullPath + " not found", vFile);
    }

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    configureByFiles(vFiles, projectFile);
  }
  protected void configureByFile(String filePath, String projectRoot) throws Exception {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    configureByFile(vFile, projectFile);
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected void configureByFile(final VirtualFile vFile) throws IOException {
    configureByFile(vFile, null);
  }

  protected void configureByExistingFile(VirtualFile virtualFile) throws IOException {
    myFile = null;
    myEditor = null;

    Editor editor = createEditor(virtualFile);

    Document document = editor.getDocument();
    EditorInfo editorInfo = new EditorInfo(document.getText());

    String newFileText = editorInfo.getNewFileText();
    document.setText(newFileText);

    PsiFile file = myPsiManager.findFile(virtualFile);
    if (myFile == null) myFile = file;

    if (myEditor == null) myEditor = editor;

    editorInfo.applyToEditor(editor);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  protected VirtualFile configureByFiles(VirtualFile[] vFiles, final File projectRoot) throws IOException {
    myFile = null;
    myEditor = null;

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    if (clearModelBeforeConfiguring()) {
      rootModel.clear();
    }
    File toDirIO = createTempDirectory();
    VirtualFile toDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(toDirIO.getCanonicalPath().replace(File.separatorChar, '/'));

    final LinkedHashMap<VirtualFile, EditorInfo> editorInfos;
    // auxiliary files should be copied first
    vFiles = ArrayUtil.reverseArray(vFiles);
    if (projectRoot != null) {
      FileUtil.copyDir(projectRoot, toDirIO);
      VirtualFile fromDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectRoot);
      editorInfos = copyFilesFillingEditorInfos(fromDir, toDir, ContainerUtil.map2Array(vFiles, String.class, new Function<VirtualFile, String>() {
        public String fun(final VirtualFile s) {
          return s.getPath().substring(projectRoot.getPath().length());
        }
      }));
    } else {
      editorInfos = new LinkedHashMap<VirtualFile, EditorInfo>();
      for (final VirtualFile vFile : vFiles) {
        editorInfos.putAll(copyFilesFillingEditorInfos(vFile.getParent(), toDir, vFile.getName()));
      }
    }

    if (isAddDirToContentRoot()) {
      final ContentEntry contentEntry = rootModel.addContentEntry(toDir);
      if (isAddDirToSource()) contentEntry.addSourceFolder(toDir, false);
    }
    rootModel.commit();

    openEditorsAndActivateLast(editorInfos);

    return toDir;
  }

  protected LinkedHashMap<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(String testDataFromDir,
                                                                               final VirtualFile toDir,
                                                                               final String... relativePaths) throws IOException {
    if (!testDataFromDir.startsWith("/")) testDataFromDir = "/" + testDataFromDir;
    return copyFilesFillingEditorInfos(LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + testDataFromDir), toDir, relativePaths);
  }

  protected LinkedHashMap<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(final VirtualFile fromDir, final VirtualFile toDir, final String... relativePaths) throws IOException {
    LinkedHashMap<VirtualFile, EditorInfo> editorInfos = new LinkedHashMap<VirtualFile, EditorInfo>();

    List<OutputStream> streamsToClose = new ArrayList<OutputStream>();

    for (String relativePath : relativePaths) {
      if (relativePath.startsWith("/")) {
        relativePath = relativePath.substring(1);
      }
      final VirtualFile fromFile = fromDir.findFileByRelativePath(relativePath);
      assertNotNull(fromDir.getPath() + "/" + relativePath, fromFile);
      VirtualFile toFile = toDir.findFileByRelativePath(relativePath);
      if (toFile == null) {
        final File file = new File(toDir.getPath(), relativePath);
        file.getParentFile().mkdirs();
        file.createNewFile();
        toFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        assertNotNull(file.getCanonicalPath(), toFile);
      }

      editorInfos.put(toFile, copyContent(fromFile, toFile, streamsToClose));
    }

    for(int i = streamsToClose.size() -1; i >= 0 ; --i) {
      streamsToClose.get(i).close();
    }
    return editorInfos;
  }

  /*protected LinkedHashMap<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(final VirtualFile fromDir, final VirtualFile toDir) throws IOException {
    final LinkedHashMap<VirtualFile, EditorInfo> map = new LinkedHashMap<VirtualFile, EditorInfo>();
    copyFilesFillingEditorInfos(fromDir, toDir, map);
    return map;
  }


  private void copyFilesFillingEditorInfos(final VirtualFile fromDir, final VirtualFile toDir, LinkedHashMap<VirtualFile, EditorInfo> editorInfos) throws IOException {

    List<OutputStream> streamsToClose = new ArrayList<OutputStream>();

    final VirtualFile[] files = fromDir.getChildren();
    for (final VirtualFile fromFile : files) {
      if (fromFile.isDirectory()) {
        copyFilesFillingEditorInfos(fromFile, toDir.createChildDirectory(this, fromFile.getName()), editorInfos);
      } else {
        final VirtualFile toFile = toDir.createChildData(this, fromFile.getName());
        editorInfos.put(toFile, copyContent(fromFile, toFile, streamsToClose));
      }
    }

    for(int i = streamsToClose.size() -1; i >= 0 ; --i) {
      streamsToClose.get(i).close();
    }
  }*/

  private EditorInfo copyContent(final VirtualFile from, final VirtualFile to, final List<OutputStream> streamsToClose) throws IOException {
    byte[] content = from.getFileType().isBinary() ? from.contentsToByteArray(): null;
    final String fileText =  from.getFileType().isBinary() ? null: StringUtil.convertLineSeparators(VfsUtil.loadText(from), "\n");

    EditorInfo editorInfo = fileText != null ? new EditorInfo(fileText) : null;
    String newFileText = fileText != null ? editorInfo.getNewFileText() : null;
    doWrite(newFileText, to, content, streamsToClose);
    return editorInfo;
  }

  protected final void setActiveEditor(Editor editor) {
    myEditor = editor;
    myFile = getPsiFile(editor.getDocument());
  }

  protected List<Editor> openEditorsAndActivateLast(final LinkedHashMap<VirtualFile, EditorInfo> editorInfos) {
    final List<Editor> list = openEditors(editorInfos);
    setActiveEditor(list.get(list.size() - 1));
    return list;
  }

  protected final List<Editor> openEditors(final LinkedHashMap<VirtualFile, EditorInfo> editorInfos) {
    return ContainerUtil.map(editorInfos.keySet(), new Function<VirtualFile, Editor>() {
      public Editor fun(final VirtualFile newVFile) {
        PsiFile file = myPsiManager.findFile(newVFile);
        if (myFile == null) myFile = file;

        Editor editor = createEditor(newVFile);
        if (myEditor == null) myEditor = editor;

        EditorInfo editorInfo = editorInfos.get(newVFile);
        if (editorInfo != null) {
          editorInfo.applyToEditor(editor);
        }
        return editor;
      }
    });
  }

  private void doWrite(final String newFileText, final VirtualFile newVFile, final byte[] content, final List<OutputStream> streamsToClose) throws IOException {
    if (newFileText != null) {
      VfsUtil.saveText(newVFile, newFileText);
    } else {
      final OutputStream outputStream = newVFile.getOutputStream(this, -1, -1);
      outputStream.write(content);
      streamsToClose.add(outputStream);
    }
  }

  protected boolean isAddDirToContentRoot() {
    return true;
  }

  protected boolean isAddDirToSource() {
    return true;
  }

  protected VirtualFile configureByFile(final VirtualFile vFile, final File projectRoot) throws IOException {
    return configureByFiles(new VirtualFile[] {vFile},projectRoot);
  }

  protected boolean clearModelBeforeConfiguring() {
    return true;
  }

  protected void setupCursorAndSelection(Editor editor) {
    Document document = editor.getDocument();
    final String text = document.getText();

    int caretIndex = text.indexOf(CARET_MARKER);
    int selStartIndex = text.indexOf(SELECTION_START_MARKER);
    int selEndIndex = text.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
    final RangeMarker selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    final String newText = document.getText();

    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newText, caretLine, 0);
      LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
      editor.getCaretModel().moveToLogicalPosition(pos);
    }

    if (selStartMarker != null) {
      editor.getSelectionModel().setSelection(selStartMarker.getStartOffset(), selEndMarker.getStartOffset());
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  protected void configure(String path, String dataName) throws Exception {
    super.configure(path, dataName);

    myEditor = createEditor(myFile.getVirtualFile());

    CodeInsightTestData data = (CodeInsightTestData) myTestDataBefore;

    LogicalPosition pos = new LogicalPosition(data.getLineNumber() - 1, data.getColumnNumber() - 1);
    myEditor.getCaretModel().moveToLogicalPosition(pos);

    int selectionEnd;
    int selectionStart = selectionEnd = myEditor.getCaretModel().getOffset();

    if (data.getSelectionStartColumnNumber() >= 0) {
      selectionStart = myEditor.logicalPositionToOffset(new LogicalPosition(data.getSelectionEndLineNumber() - 1, data.getSelectionStartColumnNumber() - 1));
      selectionEnd = myEditor.logicalPositionToOffset(new LogicalPosition(data.getSelectionEndLineNumber() - 1, data.getSelectionEndColumnNumber() - 1));
    }

    myEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);
  }

  protected void checkResultByFile(String filePath) throws Exception {
    checkResultByFile(filePath, false);
  }

  protected void checkResultByFile(String filePath, boolean stripTrailingSpaces) throws Exception {
    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (stripTrailingSpaces) {
      ((DocumentEx)myEditor.getDocument()).stripTrailingSpaces(false);
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("Cannot find file " + fullPath, vFile);
    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    Document document = EditorFactory.getInstance().createDocument(fileText);

    int caretIndex = fileText.indexOf(CARET_MARKER);
    int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
    final RangeMarker selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    String newFileText = document.getText();
    String newFileText1 = newFileText;
    if (stripTrailingSpaces) {
      Document document1 = EditorFactory.getInstance().createDocument(newFileText);
      ((DocumentEx)document1).stripTrailingSpaces(false);
      newFileText1 = document1.getText();
    }

    String text = myFile.getText();
    text = StringUtil.convertLineSeparators(text, "\n");

    assertEquals("Text mismatch in file " + filePath, newFileText1, text);

    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newFileText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, caretLine, 0);

      assertEquals("caretLine", caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      assertEquals("caretColumn", caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (selStartMarker != null && selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(newFileText, selStartMarker.getStartOffset());
      int selStartCol = selStartMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(newFileText, selEndMarker.getEndOffset());
      int selEndCol = selEndMarker.getEndOffset() - StringUtil.lineColToOffset(newFileText, selEndLine, 0);

      assertEquals("selectionStartLine", selStartLine + 1,
                   StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      assertEquals("selectionStartCol", selStartCol + 1,
                   myEditor.getSelectionModel().getSelectionStart() - StringUtil.lineColToOffset(newFileText, selStartLine, 0) + 1);

      assertEquals("selectionEndLine", selEndLine + 1,
                   StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      assertEquals("selectionEndCol", selEndCol + 1,
                   myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(newFileText, selEndLine, 0) + 1);
    }
    else {
      assertTrue("has no selection", !myEditor.getSelectionModel().hasSelection());
    }
  }

  protected void checkResult(String dataName) throws Exception {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    super.checkResult(dataName);

    CodeInsightTestData data = (CodeInsightTestData) myTestDataAfter;

    if (data.getColumnNumber() >= 0) {
      assertEquals(dataName + ":caretColumn", data.getColumnNumber(), myEditor.getCaretModel().getLogicalPosition().column + 1);
    }
    if (data.getLineNumber() >= 0) {
      assertEquals(dataName + ":caretLine", data.getLineNumber(), myEditor.getCaretModel().getLogicalPosition().line + 1);
    }

    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();
    LogicalPosition startPosition = myEditor.offsetToLogicalPosition(selectionStart);
    LogicalPosition endPosition = myEditor.offsetToLogicalPosition(selectionEnd);

    if (data.getSelectionStartColumnNumber() >= 0) {
      assertEquals(dataName + ":selectionStartColumn", data.getSelectionStartColumnNumber(), startPosition.column + 1);
    }
    if (data.getSelectionStartLineNumber() >= 0) {
      assertEquals(dataName + ":selectionStartLine", data.getSelectionStartLineNumber(), startPosition.line + 1);
    }
    if (data.getSelectionEndColumnNumber() >= 0) {
      assertEquals(dataName + ":selectionEndColumn", data.getSelectionEndColumnNumber(), endPosition.column + 1);
    }
    if (data.getSelectionEndLineNumber() >= 0) {
      assertEquals(dataName + ":selectionEndLine", data.getSelectionEndLineNumber(), endPosition.line + 1);
    }
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.EDITOR)) {
      return myEditor;
    }
    else {
      return super.getData(dataId);
    }
  }

  protected VirtualFile getVirtualFile(@NonNls String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);
    return vFile;
  }

  protected String getTestRoot(){
    return FileUtil.toSystemIndependentName(getTestDataPath());
  }

  public Editor getEditor() {
    return myEditor;
  }

}
