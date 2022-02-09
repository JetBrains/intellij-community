// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

final class TestEditorManagerImpl extends FileEditorManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(TestEditorManagerImpl.class);

  private final TestEditorSplitter myTestEditorSplitter = new TestEditorSplitter();

  private final Project myProject;
  private int counter;

  private final Map<VirtualFile, Editor> myVirtualFile2Editor = new HashMap<>();
  private VirtualFile myActiveFile;
  private static final MyLightVirtualFile LIGHT_VIRTUAL_FILE = new MyLightVirtualFile();
  private static class MyLightVirtualFile extends LightVirtualFile {
    MyLightVirtualFile() {super("Dummy.java");}
    void clearUserDataOnDispose() {
      clearUserData();
    }
  }

  TestEditorManagerImpl(@NotNull Project project) {
    myProject = project;
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        if (project == myProject) {
          closeAllFiles();
        }
      }
    });
    project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            for (VirtualFile file : getOpenFiles()) {
              if (VfsUtilCore.isAncestor(((VFileDeleteEvent)event).getFile(), file, false)) {
                closeFile(file);
              }
            }
          }
        }
      }
    });
  }

  @Override
  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull final VirtualFile file,
                                                                        final boolean focusEditor,
                                                                        boolean searchForSplitter) {
    return openFileInCommand(new OpenFileDescriptor(myProject, file));
  }

  @NotNull
  private Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(@NotNull FileEditorNavigatable openFileDescriptor) {
    VirtualFile file = openFileDescriptor.getFile();
    boolean isNewEditor = !myVirtualFile2Editor.containsKey(file);

    // for non-text editors. uml, etc
    FileEditorProvider provider = file.getUserData(FileEditorProvider.KEY);
    Pair<FileEditor[], FileEditorProvider[]> result;
    final FileEditor fileEditor;
    final Editor editor;
    if (provider != null && provider.accept(getProject(), file)) {
      fileEditor = provider.createEditor(getProject(), file);
      if (fileEditor instanceof TextEditor) {
        editor = ((TextEditor)fileEditor).getEditor();
        TextEditorProvider.putTextEditor(editor, (TextEditor)fileEditor);
      }
      else {
        editor = null;
      }
    }
    else {
      //text editor
      editor = doOpenTextEditor(openFileDescriptor);
      fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
      provider = getProvider();
    }
    result = Pair.create(new FileEditor[]{fileEditor}, new FileEditorProvider[]{provider});

    myVirtualFile2Editor.put(file, editor);
    myActiveFile = file;

    if (editor != null) {
      editor.getSelectionModel().removeSelection();
      if (openFileDescriptor instanceof OpenFileDescriptor) {
        ((OpenFileDescriptor)openFileDescriptor).navigateIn(editor);
      }
    }

    modifyTabWell(() -> {
      myTestEditorSplitter.openAndFocusTab(file, result.first[0], result.second[0]);
      if (isNewEditor) {
        eventPublisher().fileOpened(this, file);
      }
    });

    return result;
  }

  private void modifyTabWell(@NotNull Runnable tabWellModification) {
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

    eventPublisher().selectionChanged(event);
  }

  @NotNull
  private FileEditorManagerListener eventPublisher() {
    return getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
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
    return "SplitTabContainer" + counter;
  }


  @Override
  public void changeSplitterOrientation() {

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
  public FileEditorWithProvider getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    final Editor editor = getEditor(file);
    if (editor != null) {
      final TextEditorProvider textEditorProvider = TextEditorProvider.getInstance();
      final FileEditorProvider provider = Optional.ofNullable(editor.getUserData(FileEditorProvider.KEY)).orElse(textEditorProvider);
      final TextEditor fileEditor = textEditorProvider.getTextEditor(editor);
      return new FileEditorWithProvider(fileEditor, provider);
    }
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
  public Promise<EditorWindow> getActiveWindow() {
    return Promises.resolvedPromise();
  }

  @Override
  public void setCurrentWindow(EditorWindow window) {
  }

  @Override
  public VirtualFile getFile(@NotNull FileEditor editor) {
    return LIGHT_VIRTUAL_FILE;
  }

  @Override
  public void unsplitWindow() {

  }

  @Override
  public void unsplitAllWindow() {

  }

  @Override
  public EditorWindow @NotNull [] getWindows() {
    return new EditorWindow[0];
  }

  @Override
  public FileEditor getSelectedEditor(@NotNull VirtualFile file) {
    final Editor editor = getEditor(file);
    if (editor != null) {
      return TextEditorProvider.getInstance().getTextEditor(editor);
    }

    Pair<FileEditor, FileEditorProvider> editorAndProvider = myTestEditorSplitter.getEditorAndProvider(file);
    if (editorAndProvider != null) {
      return editorAndProvider.first;
    }

    return null;
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    return getEditor(file) != null;
  }

  @Override
  public FileEditor @NotNull [] getEditors(@NotNull VirtualFile file) {
    FileEditor e = getSelectedEditor(file);
    if (e == null) return FileEditor.EMPTY_ARRAY;
    return new FileEditor[] {e};
  }

  @Override
  public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
    return getEditors(file);
  }

  @Override
  public VirtualFile @NotNull [] getSiblings(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
    closeAllFiles();
    LIGHT_VIRTUAL_FILE.clearUserDataOnDispose();
  }

  @Override
  public void closeFile(@NotNull final VirtualFile file) {
    Editor editor = myVirtualFile2Editor.remove(file);
    if (editor != null){
      TextEditorProvider editorProvider = TextEditorProvider.getInstance();
      editorProvider.disposeEditor(editorProvider.getTextEditor(editor));
      if (!editor.isDisposed()) {
        EditorFactory.getInstance().releaseEditor(editor);
      }
      if (!myProject.isDisposed()) {
        eventPublisher().fileClosed(this, file);
      }
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
  public VirtualFile @NotNull [] getSelectedFiles() {
    return myActiveFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{myActiveFile};
  }

  @Override
  public FileEditor @NotNull [] getSelectedEditors() {
    return myActiveFile == null ? FileEditor.EMPTY_ARRAY : getEditors(myActiveFile);
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
  public VirtualFile @NotNull [] getOpenFiles() {
    return VfsUtilCore.toVirtualFileArray(myVirtualFile2Editor.keySet());
  }

  public Editor getEditor(VirtualFile file) {
    return myVirtualFile2Editor.get(file);
  }

  @Override
  public FileEditor @NotNull [] getAllEditors() {
    FileEditor[] result = new FileEditor[myVirtualFile2Editor.size()];
    int i = 0;
    for (Map.Entry<VirtualFile, Editor> entry : myVirtualFile2Editor.entrySet()) {
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(entry.getValue());
      result[i++] = textEditor;
    }
    return result;
  }


  @Override
  public Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    Pair<FileEditor[], FileEditorProvider[]> pair = openFileInCommand(descriptor);

    for (FileEditor editor : pair.first) {
      if (editor instanceof TextEditor) {
        return ((TextEditor)editor).getEditor();
      }
    }
    return null;
  }

  @NotNull
  private Pair<FileEditor[], FileEditorProvider[]> openFileInCommand(@NotNull FileEditorNavigatable descriptor) {
    Ref<Pair<FileEditor[], FileEditorProvider[]>> result = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> result.set(openFileImpl3(descriptor)), "", null);
    return result.get();
  }

  @NotNull
  private Editor doOpenTextEditor(@NotNull FileEditorNavigatable descriptor) {
    VirtualFile file = descriptor.getFile();
    Editor editor = myVirtualFile2Editor.get(file);

    if (editor == null) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      LOG.assertTrue(document != null, file);
      EditorFactory editorFactory = EditorFactory.getInstance();
      editor = editorFactory.createEditor(document, myProject);
      try {
        EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myProject, file);
        Language language = TextEditorImpl.getDocumentLanguage(editor);
        editor.getSettings().setLanguageSupplier(() -> language);
        EditorEx editorEx = (EditorEx)editor;
        editorEx.setHighlighter(highlighter);
        editorEx.setFile(file);
      }
      catch (Throwable e) {
        editorFactory.releaseEditor(editor);
        throw e;
      }
    }

    return editor;
  }

  @Override
  @NotNull
  public List<FileEditor> openFileEditor(@NotNull FileEditorNavigatable descriptor, boolean focusEditor) {
    Pair<FileEditor[], FileEditorProvider[]> pair = openFileInCommand(descriptor);
    return Arrays.asList(pair.first);
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

    FileEditor[] fileEditor = FileEditor.EMPTY_ARRAY;
    FileEditorProvider[] fileEditorProvider= FileEditorProvider.EMPTY_ARRAY;
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
    if (myVirtualFile2Editor.containsKey(file)) {
      modifyTabWell(() -> {
        myActiveFile = file;
        myTestEditorSplitter.setFocusedFile(file);
      });
    }
  }
}
