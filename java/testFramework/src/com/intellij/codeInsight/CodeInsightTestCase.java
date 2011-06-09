/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Mike
 */
public abstract class CodeInsightTestCase extends PsiTestCase {
  protected Editor myEditor;

  protected Editor createEditor(VirtualFile file) {
    final FileEditorManager instance = FileEditorManager.getInstance(myProject);

    if (file.getFileType().isBinary()) return null;

    Editor editor = instance.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    ((EditorImpl)editor).setCaretActive();
    return editor;
  }

  @Override
  protected void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }
    myEditor = null;
    super.tearDown();
  }

  @Override
  protected PsiTestData createData() {
    return new CodeInsightTestData();
  }

  public static final String CARET_MARKER = "<caret>";
  @NonNls public static final String SELECTION_START_MARKER = "<selection>";
  @NonNls public static final String SELECTION_END_MARKER = "</selection>";

  protected void configureByFile(@NonNls String filePath) throws Exception {
    configureByFile(filePath, null);
  }
  protected VirtualFile configureByFiles(String projectRoot,String... files) throws Exception {
    final VirtualFile[] vFiles = new VirtualFile[files.length];
    for (int i = 0; i < files.length; i++) {
      String path = files[i];
      final String fullPath = FileUtil.toSystemIndependentName(getTestDataPath() + path);
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath);
      vFiles[i] = vFile;
      assertNotNull("file " + fullPath + " not found", vFile);
    }

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    return configureByFiles(projectFile, vFiles);
  }
  protected VirtualFile configureByFile(@NonNls String filePath, String projectRoot) throws Exception {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    return configureByFile(vFile, projectFile);
  }

  protected PsiFile configureByText(final FileType fileType, @NonNls final String text) throws Exception {
    return configureByText(fileType, text, null);
  }

  protected PsiFile configureByText(final FileType fileType, @NonNls final String text, final String _extension) throws Exception {
    final String extension = _extension == null ? fileType.getDefaultExtension():_extension;

    File dir = createTempDirectory();
    final File tempFile = FileUtil.createTempFile(dir, "aaa", "." + extension, true);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager.getFileTypeByExtension(extension) != fileType) {
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(Result result) throws Exception {
          fileTypeManager.associateExtension(fileType, extension);
        }
      }.execute();
    }
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    assert vFile != null;
    VfsUtil.saveText(vFile, text);

    final VirtualFile vdir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final ContentEntry contentEntry = rootModel.addContentEntry(vdir);
        contentEntry.addSourceFolder(vdir, false);
        rootModel.commit();
      }
    });

    configureByExistingFile(vFile);

    assertEquals(fileType, myFile.getVirtualFile().getFileType());
    return myFile;
  }


  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected void configureByFile(final VirtualFile vFile) throws IOException {
    configureByFile(vFile, null);
  }

  protected void configureByExistingFile(final VirtualFile virtualFile) {
    myFile = null;
    myEditor = null;

    final Editor editor = createEditor(virtualFile);

    final Document document = editor.getDocument();
    final EditorInfo editorInfo = new EditorInfo(document.getText());

    final String newFileText = editorInfo.getNewFileText();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (!document.getText().equals(newFileText)) {
          document.setText(newFileText);
        }

        PsiFile file = myPsiManager.findFile(virtualFile);
        if (myFile == null) myFile = file;

        if (myEditor == null) myEditor = editor;

        editorInfo.applyToEditor(editor);
      }
    });


    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  protected VirtualFile configureByFiles(final File rawProjectRoot, final VirtualFile... vFiles) throws IOException {
    myFile = null;
    myEditor = null;

    final File toDirIO = createTempDirectory();
    final VirtualFile toDir = getVirtualFile(toDirIO);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
          final ModifiableRootModel rootModel = rootManager.getModifiableModel();
          if (clearModelBeforeConfiguring()) {
            rootModel.clear();
          }

          // auxiliary files should be copied first
          VirtualFile[] reversed = ArrayUtil.reverseArray(vFiles);
          final LinkedHashMap<VirtualFile, EditorInfo> editorInfos;
          if (rawProjectRoot != null) {
            final File projectRoot = rawProjectRoot.getCanonicalFile();
            FileUtil.copyDir(projectRoot, toDirIO);
            VirtualFile fromDir = getVirtualFile(projectRoot);
            editorInfos =
              copyFilesFillingEditorInfos(fromDir, toDir, ContainerUtil.map2Array(reversed, String.class, new Function<VirtualFile, String>() {
                @Override
                public String fun(final VirtualFile s) {
                  return s.getPath().substring(projectRoot.getPath().length());
                }
              }));

            toDir.refresh(false, true);
          }
          else {
            editorInfos = new LinkedHashMap<VirtualFile, EditorInfo>();
            for (final VirtualFile vFile : reversed) {
              editorInfos.putAll(copyFilesFillingEditorInfos(vFile.getParent(), toDir, vFile.getName()));
            }
          }

          boolean sourceRootAdded = false;
          if (isAddDirToContentRoot()) {
            final ContentEntry contentEntry = rootModel.addContentEntry(toDir);
            if (isAddDirToSource()) {
              sourceRootAdded = true;
              contentEntry.addSourceFolder(toDir, false);
            }
          }
          doCommitModel(rootModel);
          if (sourceRootAdded) {
            sourceRootAdded(toDir);
          }

          openEditorsAndActivateLast(editorInfos);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });


    return toDir;
  }

  protected void doCommitModel(final ModifiableRootModel rootModel) {
    rootModel.commit();
  }

  protected void sourceRootAdded(final VirtualFile dir) {
  }

  protected LinkedHashMap<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(String testDataFromDir,
                                                                               final VirtualFile toDir,
                                                                               final String... relativePaths) throws IOException {
    if (!testDataFromDir.startsWith("/")) testDataFromDir = "/" + testDataFromDir;
    return copyFilesFillingEditorInfos(LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + testDataFromDir), toDir, relativePaths);
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
        FileUtil.createIfDoesntExist(file);
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
    final String fileText = from.getFileType().isBinary() ? null : StringUtil.convertLineSeparators(VfsUtil.loadText(from));

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
      @Override
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
    return configureByFiles(projectRoot, vFile);
  }

  protected boolean clearModelBeforeConfiguring() {
    return false;
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

  @Override
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

  protected void checkResultByFile(@NonNls String filePath) throws Exception {
    checkResultByFile(filePath, false);
  }

  protected void checkResultByFile(@NonNls final String filePath, final boolean stripTrailingSpaces) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
        if (stripTrailingSpaces) {
          ((DocumentEx)myEditor.getDocument()).stripTrailingSpaces(false);
        }

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        String fullPath = getTestDataPath() + filePath;

        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
        assertNotNull("Cannot find file " + fullPath, vFile);
        String ft = null;
        try {
          ft = VfsUtil.loadText(vFile);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        String fileText = StringUtil.convertLineSeparators(ft);
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

        if (myEditor instanceof EditorWindow) {
          myEditor = ((EditorWindow)myEditor).getDelegate();
          myFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(myEditor.getDocument());
        }

        String text = myFile.getText();
        text = StringUtil.convertLineSeparators(text);

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
    });
  }

  @Override
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

  @Override
  public Object getData(String dataId) {
    return PlatformDataKeys.EDITOR.is(dataId) ? myEditor : super.getData(dataId);
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

  protected void type(char c) {
    type(c, getEditor());
  }

  protected static void type(char c, Editor editor) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    DataContext dataContext = DataManager.getInstance().getDataContext();
    if (c == '\n') {
      actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, dataContext);
      return;
    }
    TypedAction action = actionManager.getTypedAction();
    action.actionPerformed(editor, c, dataContext);
  }

  protected void caretRight() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler action = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
  protected void caretUp() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler action = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
  protected void deleteLine() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler action = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_DELETE_LINE);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }

  protected void type(@NonNls String s) {
    for (char c : s.toCharArray()) {
      type(c);
    }
  }

  protected void undo() {
    UndoManager undoManager = UndoManager.getInstance(myProject);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    undoManager.undo(textEditor);
  }

  protected void backspace() {
    backspace(getEditor());
  }
  protected void backspace(final Editor editor) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        EditorActionManager actionManager = EditorActionManager.getInstance();
        EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);

        actionHandler.execute(editor, DataManager.getInstance().getDataContext());
      }
    }, "backspace", editor.getDocument());
  }

  protected void ctrlShiftF7() {
    HighlightUsagesHandler.invoke(getProject(), getEditor(), getFile());
  }

  public static void ctrlW() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    DataContext dataContext = DataManager.getInstance().getDataContext();
    AnActionEvent event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
    event.setInjectedContext(true);
    action.actionPerformed(event);
  }

  @NotNull
  protected PsiClass findClass(@NotNull @NonNls final String name) {
    final PsiClass aClass = myJavaFacade.findClass(name, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  @NotNull
  protected PsiPackage findPackage(@NotNull @NonNls final String name) {
    final PsiPackage aPackage = myJavaFacade.findPackage(name);
    assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }
}
