/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.command.CommandProcessor;
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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class TestEditorManagerImpl extends FileEditorManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.test.TestEditorManagerImpl");

  private final TestEditorSplitter myTestEditorSplitter = new TestEditorSplitter();

  private final Project myProject;
  private int counter = 0;

  private final Map<VirtualFile, Editor> myVirtualFile2Editor = new HashMap<>();
  private VirtualFile myActiveFile;
  private static final LightVirtualFile LIGHT_VIRTUAL_FILE = new LightVirtualFile("Dummy.java");

  public TestEditorManagerImpl(@NotNull Project project) {
    myProject = project;
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);

    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(Project project) {
        if (project == myProject) {
          closeAllFiles();
        }
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
        for (VirtualFile file : getOpenFiles()) {
          if (VfsUtilCore.isAncestor(event.getFile(), file, false)) {
            closeFile(file);
          }
        }
      }
    }, myProject);

  }

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull final VirtualFile file,
                                                                        final boolean focusEditor,
                                                                        boolean searchForSplitter) {
    final Ref<Pair<FileEditor[], FileEditorProvider[]>> result = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> result.set(openFileImpl3(file, focusEditor)), "", null);
    return result.get();

  }

  private Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(final VirtualFile file, boolean focusEditor) {
    // for non-text editors. uml, etc
    final FileEditorProvider provider = file.getUserData(FileEditorProvider.KEY);
    if (provider != null && provider.accept(getProject(), file)) {
      return Pair.create(new FileEditor[]{provider.createEditor(getProject(), file)}, new FileEditorProvider[]{provider});
    }

    //text editor
    Editor editor = openTextEditor(new OpenFileDescriptor(myProject, file), focusEditor);
    assert editor != null;
    final FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    final FileEditorProvider fileEditorProvider = getProvider();
    Pair<FileEditor[], FileEditorProvider[]> result = Pair.create(new FileEditor[]{fileEditor}, new FileEditorProvider[]{fileEditorProvider});

    modifyTabWell(() -> myTestEditorSplitter.openAndFocusTab(file, fileEditor, fileEditorProvider));

    return result;
  }

  private void modifyTabWell(Runnable tabWellModification) {
    if (myProject.isDisposed()) return;
    
    FileEditor lastFocusedEditor = myTestEditorSplitter.getFocusedFileEditor();
    VirtualFile lastFocusedFile  = myTestEditorSplitter.getFocusedFile();
    FileEditorProvider oldProvider = myTestEditorSplitter.getProviderFromFocused();

    tabWellModification.run();

    FileEditor currentlyFocusedEditor = myTestEditorSplitter.getFocusedFileEditor();
    VirtualFile currentlyFocusedFile = myTestEditorSplitter.getFocusedFile();
    FileEditorProvider newProvider = myTestEditorSplitter.getProviderFromFocused();

    final FileEditorManagerEvent event =
        new FileEditorManagerEvent(this, lastFocusedFile, lastFocusedEditor, oldProvider, currentlyFocusedFile, currentlyFocusedEditor, newProvider);
    final FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);

    notifyPublisher(() -> publisher.selectionChanged(event));
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
    return ActionCallback.DONE;
  }

  @Override
  public EditorsSplitters getSplittersFor(Component c) {
    return null;
  }

  @Override
  public void createSplitter(int orientation, EditorWindow window) {
    String containerName = createNewTabbedContainerName();
    myTestEditorSplitter.setActiveTabGroup(containerName);
  }

  private String createNewTabbedContainerName() {
    counter++;
    return "SplitTabContainer" + ((Object) counter).toString();
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
    for (VirtualFile file : getOpenFiles()) {
      closeFile(file);
    }
  }

  private static FileEditorProvider getProvider() {
    return new FileEditorProvider() {
      @Override
      public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return false;
      }

      @Override
      @NotNull
      public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        throw new IncorrectOperationException();
      }

      @Override
      public void disposeEditor(@NotNull FileEditor editor) {
      }

      @Override
      @NotNull
      public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        throw new IncorrectOperationException();
      }

      @Override
      @NotNull
      public String getEditorTypeId() {
        return "";
      }

      @Override
      @NotNull
      public FileEditorPolicy getPolicy() {
        throw new IncorrectOperationException();
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
    return AsyncResult.done(null);
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
    return new EditorWindow[0];
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
  public void dispose() {
    closeAllFiles();
  }

  @Override
  public void closeFile(@NotNull final VirtualFile file) {
    Editor editor = myVirtualFile2Editor.remove(file);
    if (editor != null){
      TextEditorProvider editorProvider = TextEditorProvider.getInstance();
      editorProvider.disposeEditor(editorProvider.getTextEditor(editor));
      EditorFactory.getInstance().releaseEditor(editor);
    }
    if (Comparing.equal(file, myActiveFile)) {
      myActiveFile = null;
    }

    modifyTabWell(() -> myTestEditorSplitter.closeFile(file));
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
    return new JLabel();
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
  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
  }


  @Override
  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
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

    Pair<FileEditor, FileEditorProvider> editorAndProvider = myTestEditorSplitter.getEditorAndProvider(file);

    FileEditor[] fileEditor = new FileEditor[0];
    FileEditorProvider[] fileEditorProvider= new FileEditorProvider[0];
    if (editorAndProvider != null) {
      fileEditor = new FileEditor[] {editorAndProvider.first};
      fileEditorProvider = new FileEditorProvider[]{editorAndProvider.second};
    }

    return Pair.create(fileEditor, fileEditorProvider);
  }

  @Override
  public int getWindowSplitCount() {
    return 0;
  }

  @Override
  public boolean hasSplitOrUndockedWindows() {
    return false;
  }

  @NotNull
  @Override
  public EditorsSplitters getSplitters() {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    return ActionCallback.DONE;
  }

  @Override
  public void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId) {
  }
}
