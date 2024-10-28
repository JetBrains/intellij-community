// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class JavaCodeInsightTestCase extends JavaPsiTestCase {
  protected Editor myEditor;

  protected Editor createEditor(@NotNull VirtualFile file) {
    final FileEditorManager instance = FileEditorManager.getInstance(myProject);

    if (file.getFileType().isBinary()) return null;
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    Editor editor = instance.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    ((EditorImpl)editor).setCaretActive();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart("JavaCodeInsightTestCase.createEditor " + file);

    return editor;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myProject != null) {
        FileEditorManagerEx.getInstanceEx(myProject).closeAllFiles();
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myEditor = null;
      super.tearDown();
    }
  }

  protected void configureByFile(String filePath) throws Exception {
    configureByFile(filePath, null);
  }

  /**
   * @param files the first file will be loaded in editor
   */
  protected VirtualFile configureByFiles(@Nullable String projectRoot, String @NotNull ... files) {
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

  protected VirtualFile configureByFile(@NotNull String filePath, @Nullable String projectRoot) throws Exception {
    VirtualFile vFile = findVirtualFile(filePath);
    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    return configureByFile(vFile, projectFile);
  }

  protected PsiFile configureByText(@NotNull FileType fileType, @NotNull String text) {
    return configureByText(fileType, text, null);
  }

  protected PsiFile configureByText(final @NotNull FileType fileType, @NotNull String text, @Nullable String _extension) {
    try {
      final String extension = _extension == null ? fileType.getDefaultExtension() : _extension;

      File dir = createTempDirectory();
      final File tempFile = FileUtil.createTempFile(dir, "tempFile", "." + extension, true);
      CodeInsightTestFixtureImpl.associateExtensionTemporarily(fileType, extension, getTestRootDisposable());
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
      assert vFile != null;
      WriteAction.runAndWait(() -> {
        vFile.setCharset(StandardCharsets.UTF_8);
        VfsUtil.saveText(vFile, text);
      });

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

  protected void configureByExistingFile(final @NotNull VirtualFile virtualFile) {
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
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  protected VirtualFile configureByFiles(@Nullable File rawProjectRoot, VirtualFile @NotNull ... vFiles) throws IOException {
    myFile = null;
    myEditor = null;

    VirtualFile toDir = createVirtualDirectoryForContentFile();

    Map<VirtualFile, EditorInfo> editorInfos = WriteAction.compute(() -> {
      try {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        if (clearModelBeforeConfiguring()) {
          rootModel.clear();
        }

        // auxiliary files should be copied first
        VirtualFile[] reversed = ArrayUtil.reverseArray(vFiles);
        Map<VirtualFile, EditorInfo> editorInfos1;
        if (rawProjectRoot != null) {
          FileUtil.copyDir(rawProjectRoot, toDir.toNioPath().toFile());
          File projectRoot = rawProjectRoot.getCanonicalFile();
          VirtualFile aNull = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectRoot));
          editorInfos1 = copyFilesFillingEditorInfos(aNull, toDir, ContainerUtil.map2Array(reversed, String.class, s -> {
            return s.getPath().substring(projectRoot.getPath().length());
          }));

          toDir.refresh(false, true);
        }
        else {
          editorInfos1 = new LinkedHashMap<>();
          for (VirtualFile vFile : reversed) {
            VirtualFile parent = vFile.getParent();
            assert parent.isDirectory() : parent;
            editorInfos1.putAll(copyFilesFillingEditorInfos(parent, toDir, vFile.getName()));
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

        return editorInfos1;
      }
      catch (IOException e) {
        LOG.error(e);
        return null;
      }
    });
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);

    if (editorInfos != null) {
      List<Editor> list = openEditors(editorInfos);
      setActiveEditor(ContainerUtil.getLastItem(list));
    }

    return toDir;
  }

  protected @NotNull VirtualFile createVirtualDirectoryForContentFile() {
    return getTempDir().createVirtualDir();
  }

  protected boolean isAddDirToTests() {
    return false;
  }

  protected void doCommitModel(@NotNull ModifiableRootModel rootModel) {
    rootModel.commit();
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  protected void sourceRootAdded(final VirtualFile dir) {
  }

  protected @NotNull Map<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(@NotNull String testDataFromDir,
                                                                              @NotNull VirtualFile toDir,
                                                                              String @NotNull ... relativePaths) throws IOException {
    if (!testDataFromDir.startsWith("/")) testDataFromDir = "/" + testDataFromDir;
    return copyFilesFillingEditorInfos(LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + testDataFromDir), toDir, relativePaths);
  }

  protected @NotNull Map<VirtualFile, EditorInfo> copyFilesFillingEditorInfos(@NotNull VirtualFile fromDir,
                                                                              @NotNull VirtualFile toDir,
                                                                              String @NotNull ... relativePaths) throws IOException {
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

  private EditorInfo copyContent(@NotNull VirtualFile from, @NotNull VirtualFile to, @NotNull List<? super OutputStream> streamsToClose) throws IOException {
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

  protected final @NotNull List<Editor> openEditors(@NotNull Map<VirtualFile, EditorInfo> editorInfos) {
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
                       @NotNull List<? super OutputStream> streamsToClose) throws IOException {
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

  protected void checkResultByFile(@NotNull String filePath) throws Exception {
    checkResultByFile(filePath, false);
  }

  protected void checkResultByFile(final @NotNull String filePath, final boolean stripTrailingSpaces) throws Exception {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
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
      if (!Objects.equals(expectedText, actualText)) {
        throw new FileComparisonFailedError("Text mismatch in file " + filePath, expectedText, actualText, vFile.getPath());
      }

      EditorTestUtil.verifyCaretAndSelectionState(myEditor, caretState);
    });
  }

  protected @NotNull VirtualFile findVirtualFile(@NotNull String filePath) {
    String absolutePath = getTestDataPath() + filePath;
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), absolutePath);
    return VfsTestUtil.findFileByCaseSensitivePath(absolutePath);
  }

  protected @NotNull String getTestRoot(){
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
    LightPlatformCodeInsightTestCase.deleteLine(myEditor, getProject());
  }

  protected void type(@NotNull String s) {
    for (char c : s.toCharArray()) {
      type(c);
    }
  }

  protected void backspace() {
    backspace(getEditor());
  }

  protected void backspace(final @NotNull Editor editor) {
    LightPlatformCodeInsightTestCase.backspace(editor,getProject());
  }

  protected void ctrlW() {
    LightPlatformCodeInsightTestCase.ctrlW(getEditor(),getProject());
  }

  protected void ctrlD() {
    LightPlatformCodeInsightTestCase.ctrlD(getEditor(),getProject());
  }

  protected void delete(final @NotNull Editor editor) {
    LightPlatformCodeInsightTestCase.delete(editor, getProject());
  }

  protected @NotNull PsiClass findClass(final @NotNull String name) {
    final PsiClass aClass = myJavaFacade.findClass(name, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  protected @NotNull PsiPackage findPackage(final @NotNull String name) {
    final PsiPackage aPackage = myJavaFacade.findPackage(name);
    assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }

  protected void setLanguageLevel(@NotNull LanguageLevel level) {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), level);
  }
}