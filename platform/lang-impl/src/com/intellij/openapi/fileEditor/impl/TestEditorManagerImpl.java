/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@NonNls
public class TestEditorManagerImpl extends FileEditorManagerEx implements ApplicationComponent, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.test.TestEditorManagerImpl");

  private final Project myProject;

  private final Map<VirtualFile, Editor> myVirtualFile2Editor = new HashMap<VirtualFile,Editor>();
  private VirtualFile myActiveFile = null;
  private static final LightVirtualFile LIGHT_VIRTUAL_FILE = new LightVirtualFile("Dummy.java");

  public TestEditorManagerImpl(Project project) {
    myProject = project;
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);
  }

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                        boolean focusEditor,
                                                                        boolean searchForSplitter) {
    // for non-text editors. uml, etc
    final FileEditorProvider provider = file.getUserData(FileEditorProvider.KEY);
    if (provider != null && provider.accept(getProject(), file)) {
      return Pair.create(new FileEditor[]{provider.createEditor(getProject(), file)}, new FileEditorProvider[]{provider});
    }

    //text editor
    Editor editor = openTextEditor(new OpenFileDescriptor(myProject, file), focusEditor);
    final FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    return Pair.create (new FileEditor[] {fileEditor}, new FileEditorProvider[] {getProvider (fileEditor)});
  }

  @NotNull
  @Override
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                        boolean focusEditor,
                                                                        @NotNull EditorWindow window) {
    return openFileWithProviders(file, focusEditor, false);
  }

  @Override
  public boolean isInsideChange() {
    return false;
  }

  @NotNull
  @Override
  public ActionCallback notifyPublisher(@NotNull Runnable runnable) {
    runnable.run();
    return new ActionCallback.Done();
  }

  @Override
  public EditorsSplitters getSplittersFor(Component c) {
    return null;
  }

  @Override
  public void createSplitter(int orientation, EditorWindow window) {

  }

  @Override
  public void changeSplitterOrientation() {

  }

  @Override
  public void flipTabs() {

  }

  @Override
  public boolean tabsMode() {
    return false;
  }

  @Override
  public boolean isInSplitter() {
    return false;
  }

  @Override
  public boolean hasOpenedFile() {
    return false;
  }

  @Override
  public VirtualFile getCurrentFile() {
    return myActiveFile;
  }

  @Override
  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public boolean isChanged(@NotNull EditorComposite editor) {
    return false;
  }

  @Override
  public EditorWindow getNextWindow(@NotNull EditorWindow window) {
    return null;
  }

  @Override
  public EditorWindow getPrevWindow(@NotNull EditorWindow window) {
    return null;
  }

  @Override
  public void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  @Override
  public void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  @Override
  public void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  @Override
  public void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  @Override
  public void closeAllFiles() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    Iterator<Editor> it = myVirtualFile2Editor.values().iterator();
    while (it.hasNext()) {
      Editor editor = it.next();
      it.remove();
      if (editor != null && !editor.isDisposed()){
        editorFactory.releaseEditor(editor);
      }
    }
  }

  public Editor openTextEditorEnsureNoFocus(@NotNull OpenFileDescriptor descriptor) {
    return openTextEditor(descriptor, false);
  }

  private FileEditorProvider getProvider(FileEditor editor) {
    return new FileEditorProvider() {
      @Override
      public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return false;
      }

      @Override
      @NotNull
      public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return null;
      }

      @Override
      public void disposeEditor(@NotNull FileEditor editor) {
        //Disposer.dispose(editor);
      }

      @Override
      @NotNull
      public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return null;
      }

      @Override
      public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {

      }

      @Override
      @NotNull
      public String getEditorTypeId() {
        return "";
      }

      @Override
      @NotNull
      public FileEditorPolicy getPolicy() {
        return null;
      }
    };
  }

  @Override
  public EditorWindow getCurrentWindow() {
    return null;
  }

  @NotNull
  @Override
  public AsyncResult<EditorWindow> getActiveWindow() {
    return new AsyncResult.Done<EditorWindow>(null);
  }

  @Override
  public void setCurrentWindow(EditorWindow window) {
  }

  @Override
  public VirtualFile getFile(@NotNull FileEditor editor) {
    return LIGHT_VIRTUAL_FILE;
  }

  @Override
  public void updateFilePresentation(@NotNull VirtualFile file) {
  }

  @Override
  public void unsplitWindow() {

  }

  @Override
  public void unsplitAllWindow() {

  }

  @Override
  @NotNull
  public EditorWindow[] getWindows() {
    return new EditorWindow[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public FileEditor getSelectedEditor(@NotNull VirtualFile file) {
    final Editor editor = getEditor(file);
    return editor == null ? null : TextEditorProvider.getInstance().getTextEditor(editor);
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    return getEditor(file) != null;
  }

  @Override
  @NotNull
  public FileEditor[] getEditors(@NotNull VirtualFile file) {
    FileEditor e = getSelectedEditor(file);
    if (e == null) return new FileEditor[0];
    return new FileEditor[] {e};
  }

  @NotNull
  @Override
  public FileEditor[] getAllEditors(@NotNull VirtualFile file) {
    return getEditors(file);
  }

  @Override
  @NotNull
  public VirtualFile[] getSiblings(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void disposeComponent() {
    closeAllFiles();
  }

  @Override
  public void initComponent() { }

  @Override
  public void projectClosed() {
    closeAllFiles();
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void closeFile(@NotNull VirtualFile file) {
    Editor editor = myVirtualFile2Editor.remove(file);
    if (editor != null){
      EditorFactory.getInstance().releaseEditor(editor);
    }
    if (Comparing.equal(file, myActiveFile)) myActiveFile = null;
  }

  @Override
  public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    closeFile(file);
  }

  @Override
  @NotNull
  public VirtualFile[] getSelectedFiles() {
    return myActiveFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{myActiveFile};
  }

  @Override
  @NotNull
  public FileEditor[] getSelectedEditors() {
    return new FileEditor[0];
  }

  @Override
  public Editor getSelectedTextEditor() {
    return myActiveFile != null ? getEditor(myActiveFile) : null;
  }

  @Override
  public JComponent getComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public VirtualFile[] getOpenFiles() {
    return VfsUtilCore.toVirtualFileArray(myVirtualFile2Editor.keySet());
  }

  public Editor getEditor(VirtualFile file) {
    return myVirtualFile2Editor.get(file);
  }

  @Override
  @NotNull
  public FileEditor[] getAllEditors() {
    FileEditor[] result = new FileEditor[myVirtualFile2Editor.size()];
    int i = 0;
    for (Map.Entry<VirtualFile, Editor> entry : myVirtualFile2Editor.entrySet()) {
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(entry.getValue());
      result[i++] = textEditor;
    }
    return result;
  }

  @Override
  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }


  @Override
  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }

  public void registerFileAsOpened(VirtualFile file, Editor editor) {
    myVirtualFile2Editor.put(file, editor);
    myActiveFile = file;
  }

  @Override
  public Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    final VirtualFile file = descriptor.getFile();
    Editor editor = myVirtualFile2Editor.get(file);

    if (editor == null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      LOG.assertTrue(psiFile != null, file);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
      LOG.assertTrue(document != null, psiFile);
      editor = EditorFactory.getInstance().createEditor(document, myProject);
      final EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myProject, file);
      ((EditorEx) editor).setHighlighter(highlighter);
      ((EditorEx) editor).setFile(file);

      myVirtualFile2Editor.put(file, editor);
    }

    if (descriptor.getOffset() >= 0){
      editor.getCaretModel().moveToOffset(descriptor.getOffset());
    }
    else if (descriptor.getLine() >= 0 && descriptor.getColumn() >= 0){
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(descriptor.getLine(), descriptor.getColumn()));
    }
    editor.getSelectionModel().removeSelection();
    myActiveFile = file;

    return editor;
  }

  @Override
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  @Override
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  @Override
  @NotNull
  public List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
    return Pair.create(new FileEditor[0], new FileEditorProvider[0]);
  }

  @Override
  public int getWindowSplitCount() {
    return 0;
  }

  @Override
  public boolean hasSplitOrUndockedWindows() {
    return false;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "TestEditorManager";
  }

  @NotNull
  @Override
  public EditorsSplitters getSplitters() {
    return null;
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    return new ActionCallback.Done();
  }

  @Override
  public void setSelectedEditor(@NotNull VirtualFile file, String fileEditorProviderId) {
  }
}
