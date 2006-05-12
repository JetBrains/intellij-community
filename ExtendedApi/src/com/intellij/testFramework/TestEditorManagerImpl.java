package com.intellij.testFramework;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@NonNls public class TestEditorManagerImpl extends FileEditorManagerEx implements ApplicationComponent, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.test.TestEditorManagerImpl");

  private final Project myProject;

  private Map<VirtualFile, Editor> myVirtualFile2Editor = new HashMap<VirtualFile,Editor>();
  private VirtualFile myActiveFile = null;
  private static final LightVirtualFile LIGHT_VIRTUAL_FILE = new LightVirtualFile("Dummy.java");

  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(VirtualFile file, boolean focusEditor) {
    Editor editor = openTextEditor(new OpenFileDescriptor(myProject, file), focusEditor);
    final FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    return Pair.create (new FileEditor[] {fileEditor}, new FileEditorProvider[] {getProvider (fileEditor)});
  }

  public void moveFocusToNextEditor() {
    throw new UnsupportedOperationException();
  }

  public void createSplitter(int orientation) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void changeSplitterOrientation() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void flipTabs() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean tabsMode() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean isInSplitter() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean hasOpenedFile() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public VirtualFile getCurrentFile() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(VirtualFile file) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean isChanged(EditorComposite editor) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public EditorWindow getNextWindow(EditorWindow window) {
    return null;
  }

  public EditorWindow getPrevWindow(EditorWindow window) {
    return null;
  }

  public void closeAllFiles() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    for (VirtualFile file : myVirtualFile2Editor.keySet()) {
      Editor editor = myVirtualFile2Editor.get(file);
      if (editor != null){
        editorFactory.releaseEditor(editor);
      }
    }
    myVirtualFile2Editor.clear();
  }

  public Editor openTextEditorEnsureNoFocus(OpenFileDescriptor descriptor) {
    return openTextEditor(descriptor, false);
  }

  public FileEditorProvider getProvider(FileEditor editor) {
    return new FileEditorProvider() {
      public boolean accept(Project project, VirtualFile file) {
        return false;
      }

      public FileEditor createEditor(Project project, VirtualFile file) {
        return null;
      }

      public void disposeEditor(FileEditor editor) {

      }

      @NotNull
      public FileEditorState readState(Element sourceElement, Project project, VirtualFile file) {
        return null;
      }

      public void writeState(FileEditorState state, Project project, Element targetElement) {

      }

      @NotNull
      public String getEditorTypeId() {
        return "";
      }

      public FileEditorPolicy getPolicy() {
        return null;
      }
    };
  }

  public EditorWindow getCurrentWindow() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void setCurrentWindow(EditorWindow window) {
  }

  public VirtualFile getFile(FileEditor editor) {
    return LIGHT_VIRTUAL_FILE;
  }

  public boolean hasSplitters() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean hasTabGroups() {
    throw new UnsupportedOperationException();
  }

  public boolean isFilePinned(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public void setFilePinned(VirtualFile file, boolean pinned) {
    throw new UnsupportedOperationException();
  }

  public void unsplitWindow() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void unsplitAllWindow() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public EditorWindow[] getWindows() {
    return new EditorWindow[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int getTabGroupsOrientation() {
    throw new UnsupportedOperationException();
  }

  public void setTabGroupsOrientation(int orientation) {
    throw new UnsupportedOperationException();
  }

  public void moveToOppositeTabGroup(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public FileEditor getSelectedEditor(VirtualFile file) {
    final Editor editor = getEditor(file);
    return editor == null ? null : TextEditorProvider.getInstance().getTextEditor(editor);
  }

  public boolean isFileOpen(VirtualFile file) {
    return getEditor(file) != null;
  }

  public FileEditor[] getEditors(VirtualFile file) {
    return new FileEditor[] {getSelectedEditor(file)};
  }

  public TestEditorManagerImpl(Project project) {
    myProject = project;
  }

  public VirtualFile[] getSiblings(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public void disposeComponent() {
    closeAllFiles();
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void closeFile(VirtualFile file) {
    Editor editor = myVirtualFile2Editor.get(file);
    if (editor != null){
      EditorFactory.getInstance().releaseEditor(editor);
      myVirtualFile2Editor.remove(file);
    }
  }

  public void closeFile(VirtualFile file, EditorWindow window) {
    closeFile(file);
  }

  public VirtualFile[] getSelectedFiles() {
    return myActiveFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{myActiveFile};
  }

  public FileEditor[] getSelectedEditors() {
    return new FileEditor[0];
  }

  public Editor getSelectedTextEditor() {
    return myActiveFile != null ? getEditor(myActiveFile) : null;
  }

  public JComponent getComponent() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getOpenFiles() {
    return myVirtualFile2Editor.keySet().toArray(new VirtualFile[myVirtualFile2Editor.size()]);
  }

  public Editor getEditor(VirtualFile file) {
    return myVirtualFile2Editor.get(myActiveFile);
  }

  public FileEditor[] getAllEditors(){
    throw new UnsupportedOperationException();
  }

  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }


  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }

  public void registerFileAsOpened(VirtualFile file, Editor editor) {
    myVirtualFile2Editor.put(file, editor);
    myActiveFile = file;
  }

  public Editor openTextEditor(OpenFileDescriptor descriptor, boolean focusEditor) {
    final VirtualFile file = descriptor.getFile();
    Editor editor = myVirtualFile2Editor.get(file);

    if (editor == null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      LOG.assertTrue(psiFile != null);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
      editor = EditorFactory.getInstance().createEditor(document, myProject);
      ((EditorEx) editor).setHighlighter(HighlighterFactory.createHighlighter(myProject, file));

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

  public void addFileEditorManagerListener(FileEditorManagerListener listener) {
  }

  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, Disposable parentDisposable) {
  }

  public void removeFileEditorManagerListener(FileEditorManagerListener listener) {
  }

  @NotNull
  public List<FileEditor> openEditor(OpenFileDescriptor descriptor, boolean focusEditor) {
    return Collections.emptyList();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public JComponent getPreferredFocusedComponent() {
    throw new UnsupportedOperationException();
  }

  public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(VirtualFile file) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public String getComponentName() {
    return "TestEditorManager";
  }
}
