package ru.compscicenter.edide;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HideableTitledPanel;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
* User: lia
* Date: 23.05.14
* Time: 14:16
*/
public class StudyEditor implements FileEditor {
  private final FileEditor myDefaultEditor;
  private final JComponent myComponent;
  private String getTextForTask(VirtualFile file, Project project) {
    //int taskNum = TaskManager.getInstance(project).getTaskNumForFile(file.getName());
    //return TaskManager.getInstance(project).getTaskText(taskNum);
    return "Sample text";
  }

  public StudyEditor(Project project, VirtualFile file) {
    myDefaultEditor = TextEditorProvider.getInstance().createEditor(project, file);
    myComponent = myDefaultEditor.getComponent();
    JPanel studyPanel = new JPanel(new GridLayout(2, 1));
    final JLabel taskText = new JLabel(getTextForTask(file, project));
    taskText.setFont(new Font("Arial", Font.PLAIN, 16));
    HideableTitledPanel taskTextPanel = new HideableTitledPanel("Task text", taskText, true);
    studyPanel.add(taskTextPanel);
    JPanel studyButtonPanel = new JPanel(new GridLayout(1, 6));
    JButton checkButton = new JButton("button1");
    checkButton.setIcon(StudyIcons.Resolve);
    studyButtonPanel.add(checkButton);
    studyButtonPanel.add(new JButton("button2"));
    studyPanel.add(studyButtonPanel);
    myComponent.add(studyPanel, BorderLayout.NORTH);
  }

  public FileEditor getDefaultEditor() {
    return myDefaultEditor;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComponent;
  }

  @NotNull
  @Override
  public String getName() {
    return "Study Editor";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myDefaultEditor.getState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    myDefaultEditor.setState(state);
  }

  @Override
  public boolean isModified() {
    return myDefaultEditor.isModified();
  }

  @Override
  public boolean isValid() {
    return myDefaultEditor.isValid();
  }

  @Override
  public void selectNotify() {
    myDefaultEditor.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myDefaultEditor.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDefaultEditor.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDefaultEditor.removePropertyChangeListener(listener);
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myDefaultEditor.getBackgroundHighlighter();
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return myDefaultEditor.getCurrentLocation();
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return myDefaultEditor.getStructureViewBuilder();
  }

  @Override
  public void dispose() {
    myDefaultEditor.dispose();
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myDefaultEditor.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDefaultEditor.putUserData(key, value);
  }

  public static Editor getSelectedEditor(Project project) {
    Editor selectedEditor = null;
    FileEditor fileEditor =
      FileEditorManagerImpl.getInstanceEx(project).getSplitters().getCurrentWindow().getSelectedEditor().getSelectedEditorWithProvider()
        .getFirst();
    if (fileEditor instanceof StudyEditor) {
      FileEditor defaultEditor = ((StudyEditor)fileEditor).getDefaultEditor();
      if (defaultEditor instanceof PsiAwareTextEditorImpl) {
        selectedEditor = ((PsiAwareTextEditorImpl)defaultEditor).getEditor();
      }
    }
    return selectedEditor;
  }

}
