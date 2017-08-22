/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.DocumentImpl;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Mike
 */
public abstract class CodeInsightTestCase extends PsiTestCase {
  protected Editor myEditor;

  protected Editor createEditor(@NotNull VirtualFile file) {
    final FileEditorManager instance = FileEditorManager.getInstance(myProject);

    if (file.getFileType().isBinary()) return null;
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    Editor editor = instance.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    ((EditorImpl)editor).setCaretActive();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    DaemonCodeAnalyzer.getInstance(getProject()).restart();

    return editor;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fixTemplates();
  }

  public static void fixTemplates() {
    FileTemplateManager manager = FileTemplateManager.getDefaultInstance();
    for (String tname : Arrays.asList("Class", "AnnotationType", "Enum", "Interface")) {
      for (FileTemplate template : manager.getInternalTemplates()) {
        if (tname.equals(template.getName())) {
          String text = "package $PACKAGE_NAME$;\npublic " + manager.internalTemplateToSubject(tname) + " $NAME$ { }";
          template.setText(FileTemplateManagerImpl.normalizeText(text));
          break;
        }
      }
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myProject != null) {
        FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
        for (VirtualFile openFile : editorManager.getOpenFiles()) {
          editorManager.closeFile(openFile);
        }
      }
    }
    finally {
      myEditor = null;
      super.tearDown();
    }
  }

  @Override
  protected PsiTestData createData() {
    return new CodeInsightTestData();
  }

  protected void configureByFile(String filePath) throws Exception {
    configureByFile(filePath, null);
  }

  /**
   * @param files the first file will be loaded in editor
   */
  protected VirtualFile configureByFiles(@Nullable String projectRoot, @NotNull String... files) {
    if (files.length == 0) return null;
    final VirtualFile[] vFiles = new VirtualFile[files.length];
    for (int i = 0; i < files.length; i++) {
      vFiles[i] = findVirtualFile(files[i]);
      if (vFiles[i] != null) {
        VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(vFiles[i], files[i]);
      }
    }

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    try {
      return configureByFiles(projectFile, vFiles);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void allowRootAccess(final String filePath) {
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), filePath);
  }

  protected VirtualFile configureByFile(String filePath, @Nullable String projectRoot) throws Exception {
    VirtualFile vFile = findVirtualFile(filePath);
    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    return configureByFile(vFile, projectFile);
  }

  protected PsiFile configureByText(@NotNull FileType fileType, @NotNull final String text) {
    return configureByText(fileType, text, null);
  }

  protected PsiFile configureByText(@NotNull final FileType fileType, @NotNull final String text, @Nullable String _extension) {
    try {
      final String extension = _extension == null ? fileType.getDefaultExtension():_extension;

      File dir = createTempDirectory();
      final File tempFile = FileUtil.createTempFile(dir, "tempFile", "." + extension, true);
      final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      if (fileTypeManager.getFileTypeByExtension(extension) != fileType) {
        new WriteCommandAction(getProject()) {
          @Override
          protected void run(@NotNull Result result) throws Exception {
            fileTypeManager.associateExtension(fileType, extension);
          }
        }.execute();
      }
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
      assert vFile != null;
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          vFile.setCharset(CharsetToolkit.UTF8_CHARSET);
          VfsUtil.saveText(vFile, text);
        }
      }.execute();

      final VirtualFile vdir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);

      PsiTestUtil.addSourceRoot(myModule, vdir);

      configureByExistingFile(vFile);

      assertEquals(fileType, myFile.getVirtualFile().getFileType());
      return myFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  protected void configureByFile(@NotNull VirtualFile vFile) throws IOException {
    configureByFile(vFile, null);
  }

  protected void configureByExistingFile(@NotNull final VirtualFile virtualFile) {
    myFile = null;
    myEditor = null;

    final Editor editor = createEditor(virtualFile);

    final Document document = editor.getDocument();
    final EditorInfo editorInfo = new EditorInfo(document.getText());

    final String newFileText = editorInfo.getNewFileText();
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!document.getText().equals(newFileText)) {
        document.setText(newFileText);
      }

      PsiFile file = myPsiManager.findFile(virtualFile);
      if (myFile == null) myFile = file;

      if (myEditor == null) myEditor = editor;

      editorInfo.applyToEditor(editor);
    });


    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  protected VirtualFile configureByFiles(@Nullable final File rawProjectRoot, @NotNull final VirtualFile... vFiles) throws IOException {
    myFile = null;
    myEditor = null;

    final File toDirIO = createTempDirectory();
    final VirtualFile toDir = getVirtualFile(toDirIO);

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        if (clearModelBeforeConfiguring()) {
          rootModel.clear();
        }

        // auxiliary files should be copied first
        VirtualFile[] reversed = ArrayUtil.reverseArray(vFiles);
        Map<VirtualFile, EditorInfo> editorInfos;
        if (rawProjectRoot != null) {
          final File projectRoot = rawProjectRoot.getCanonicalFile();
          FileUtil.copyDir(projectRoot, toDirIO);
          VirtualFile fromDir = getVirtualFile(projectRoot);
          editorInfos =
            copyFilesFillingEditorInfos(fromDir, toDir, ContainerUtil.map2Array(reversed, String.class, s -> s.getPath().substring(projectRoot.getPath().length())));

          toDir.refresh(false, true);
        }
        else {
          editorInfos = new LinkedHashMap<>();
          for (final VirtualFile vFile : reversed) {
            VirtualFile parent = vFile.getParent();
            assert parent.isDirectory() : parent;
            editorInfos.putAll(copyFilesFillingEditorInfos(parent, toDir, vFile.getName()));
          }
        }

        boolean sourceRootAdded = false;
        if (isAddDirToContentRoot()) {
          final ContentEntry contentEntry = rootModel.addContentEntry(toDir);
          if (isAddDirToSource()) {
            sourceRootAdded = true;
            contentEntry.addSourceFolder(toDir, isAddDirToTests());
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
    });


    return toDir;
  }

  protected boolean isAddDirToTests() {
    return false;
  }

  protected void doCommitModel(@NotNull ModifiableRootModel rootModel) {
    rootModel.commit();
  }

  protected void sourceRootAdded(final VirtualFile dir) {
  }

  @NotNull
  protected Map<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(@NotNull String testDataFromDir,
                                                                     @NotNull VirtualFile toDir,
                                                                     @NotNull String... relativePaths) throws IOException {
    if (!testDataFromDir.startsWith("/")) testDataFromDir = "/" + testDataFromDir;
    return copyFilesFillingEditorInfos(LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + testDataFromDir), toDir, relativePaths);
  }

  @NotNull
  protected Map<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(@NotNull VirtualFile fromDir,
                                                                     @NotNull VirtualFile toDir,
                                                                     @NotNull String... relativePaths) throws IOException {
    Map<VirtualFile, EditorInfo> editorInfos = new LinkedHashMap<>();

    List<OutputStream> streamsToClose = new ArrayList<>();

    for (String relativePath : relativePaths) {
      relativePath = StringUtil.trimStart(relativePath, "/");
      final VirtualFile fromFile = fromDir.findFileByRelativePath(relativePath);
      assertNotNull(fromDir.getPath() + "/" + relativePath, fromFile);
      VirtualFile toFile = toDir.findFileByRelativePath(relativePath);
      if (toFile == null) {
        final File file = new File(toDir.getPath(), relativePath);
        FileUtil.createIfDoesntExist(file);
        toFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        assertNotNull(file.getCanonicalPath(), toFile);
      }
      toFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, FileUtil.toSystemDependentName(fromFile.getPath()));
      editorInfos.put(toFile, copyContent(fromFile, toFile, streamsToClose));
    }

    for(int i = streamsToClose.size() -1; i >= 0 ; --i) {
      streamsToClose.get(i).close();
    }
    return editorInfos;
  }

  private EditorInfo copyContent(@NotNull VirtualFile from, @NotNull VirtualFile to, @NotNull List<OutputStream> streamsToClose) throws IOException {
    byte[] content = from.getFileType().isBinary() ? from.contentsToByteArray(): null;
    final String fileText = from.getFileType().isBinary() ? null : StringUtil.convertLineSeparators(VfsUtilCore.loadText(from));

    EditorInfo editorInfo = fileText == null ? null : new EditorInfo(fileText);
    String newFileText = fileText == null ? null : editorInfo.getNewFileText();
    doWrite(newFileText, to, content, streamsToClose);
    return editorInfo;
  }

  protected final void setActiveEditor(@NotNull Editor editor) {
    myEditor = editor;
    myFile = getPsiFile(editor.getDocument());
  }

  @NotNull
  protected List<Editor> openEditorsAndActivateLast(@NotNull Map<VirtualFile, EditorInfo> editorInfos) {
    final List<Editor> list = openEditors(editorInfos);
    setActiveEditor(list.get(list.size() - 1));
    return list;
  }

  @NotNull
  protected final List<Editor> openEditors(@NotNull final Map<VirtualFile, EditorInfo> editorInfos) {
    return ContainerUtil.map(editorInfos.keySet(), newVFile -> {
      PsiFile file = myPsiManager.findFile(newVFile);
      if (myFile == null) myFile = file;

      Editor editor = createEditor(newVFile);
      if (myEditor == null) myEditor = editor;

      EditorInfo editorInfo = editorInfos.get(newVFile);
      if (editorInfo != null) {
        editorInfo.applyToEditor(editor);
      }
      return editor;
    });
  }

  private void doWrite(final String newFileText,
                       @NotNull VirtualFile newVFile,
                       byte[] content,
                       @NotNull List<OutputStream> streamsToClose) throws IOException {
    if (newFileText == null) {
      final OutputStream outputStream = newVFile.getOutputStream(this, -1, -1);
      outputStream.write(content);
      streamsToClose.add(outputStream);
    }
    else {
      setFileText(newVFile, newFileText);
    }
  }

  protected boolean isAddDirToContentRoot() {
    return true;
  }

  protected boolean isAddDirToSource() {
    return true;
  }

  protected VirtualFile configureByFile(@NotNull VirtualFile vFile, File projectRoot) throws IOException {
    return configureByFiles(projectRoot, vFile);
  }

  protected boolean clearModelBeforeConfiguring() {
    return false;
  }

  protected void setupCursorAndSelection(@NotNull final Editor editor) {
    Document document = editor.getDocument();
    EditorTestUtil.CaretAndSelectionState caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document);
    EditorTestUtil.setCaretsAndSelection(editor, caretState);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  protected void configure(@NotNull String path, String dataName) throws Exception {
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

  protected void checkResultByFile(@NotNull String filePath) throws Exception {
    checkResultByFile(filePath, false);
  }

  protected void checkResultByFile(@NotNull final String filePath, final boolean stripTrailingSpaces) throws Exception {
    new WriteCommandAction<Document>(getProject()) {
      @SuppressWarnings("ConstantConditions")
      @Override
      protected void run(@NotNull Result<Document> result) throws Throwable {
        getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
        if (stripTrailingSpaces) {
          ((DocumentImpl)myEditor.getDocument()).stripTrailingSpaces(getProject());
        }

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        VirtualFile vFile = findVirtualFile(filePath);

        VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(vFile, filePath);
        String expectedText;
        try {
          expectedText = VfsUtilCore.loadText(vFile);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        expectedText = StringUtil.convertLineSeparators(expectedText);
        Document document = EditorFactory.getInstance().createDocument(expectedText);

        EditorTestUtil.CaretAndSelectionState caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document);

        expectedText = document.getText();
        if (stripTrailingSpaces) {
          Document document1 = EditorFactory.getInstance().createDocument(expectedText);
          ((DocumentImpl)document1).stripTrailingSpaces(getProject());
          expectedText = document1.getText();
        }

        if (myEditor instanceof EditorWindow) {
          myEditor = ((EditorWindow)myEditor).getDelegate();
        }
        myFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(myEditor.getDocument());

        String actualText = StringUtil.convertLineSeparators(myFile.getText());

        if (!Comparing.equal(expectedText, actualText)) {
            throw new FileComparisonFailure("Text mismatch in file " + filePath, expectedText, actualText, vFile.getPath());
        }

        EditorTestUtil.verifyCaretAndSelectionState(myEditor, caretState);
      }
    }.execute();
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

  @NotNull
  protected VirtualFile getVirtualFile(@NotNull String filePath) {
    return findVirtualFile(filePath);
  }

  @NotNull
  protected VirtualFile findVirtualFile(@NotNull String filePath) {
    String absolutePath = getTestDataPath() + filePath;
    allowRootAccess(absolutePath);
    return VfsTestUtil.findFileByCaseSensitivePath(absolutePath);
  }

  @NotNull
  protected String getTestRoot(){
    return FileUtil.toSystemIndependentName(getTestDataPath());
  }

  public Editor getEditor() {
    return myEditor;
  }

  protected void type(char c) {
    LightPlatformCodeInsightTestCase.type(c, getEditor(),getProject());
  }

  protected void undo() {
    UndoManager undoManager = UndoManager.getInstance(myProject);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    undoManager.undo(textEditor);
  }

  protected void caretLeft() {
    caretLeft(getEditor());
  }
  protected void caretLeft(@NotNull Editor editor) {
    LightPlatformCodeInsightTestCase.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, editor, getProject());
  }
  protected void caretRight() {
    caretRight(getEditor());
  }
  protected void caretRight(@NotNull Editor editor) {
    LightPlatformCodeInsightTestCase.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, editor, getProject());
  }
  protected void caretUp() {
    LightPlatformCodeInsightTestCase.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, myEditor, getProject());
  }

  protected void deleteLine() {
    LightPlatformCodeInsightTestCase.deleteLine(myEditor,getProject());
  }

  protected void type(@NotNull String s) {
    for (char c : s.toCharArray()) {
      type(c);
    }
  }

  protected void backspace() {
    backspace(getEditor());
  }

  protected void backspace(@NotNull final Editor editor) {
    LightPlatformCodeInsightTestCase.backspace(editor,getProject());
  }

  protected void ctrlW() {
    LightPlatformCodeInsightTestCase.ctrlW(getEditor(),getProject());
  }

  protected void ctrlD() {
    LightPlatformCodeInsightTestCase.ctrlD(getEditor(),getProject());
  }

  protected void delete(@NotNull final Editor editor) {
    LightPlatformCodeInsightTestCase.delete(editor, getProject());
  }

  @NotNull
  protected PsiClass findClass(@NotNull final String name) {
    final PsiClass aClass = myJavaFacade.findClass(name, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  @NotNull
  protected PsiPackage findPackage(@NotNull final String name) {
    final PsiPackage aPackage = myJavaFacade.findPackage(name);
    assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }
}
