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
import com.intellij.ui.components.JBScrollPane;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.Task;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.*;

/**
* User: lia
* Date: 23.05.14
* Time: 14:16
*/
public class StudyEditor implements FileEditor {
  private final FileEditor myDefaultEditor;
  private final JComponent myComponent;
  private String getTextForTask(VirtualFile file, Project project) {
    Task currentTask = StudyTaskManager.getInstance(project).getTaskFile(file).getTask();
    String textFileName = currentTask.getText();
    File textFile = new File(file.getParent().getCanonicalPath(), textFileName);
    StringBuilder taskText = new StringBuilder();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)));
      while(reader.ready()) {
        taskText.append(reader.readLine());
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return taskText.toString();
  }

  private JButton addButton(JComponent parentComponent, String toolTipText, Icon icon) {
    JButton newButton = new JButton();
    newButton.setToolTipText(toolTipText);
    newButton.setIcon(icon);
    newButton.setSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    parentComponent.add(newButton);
    return newButton;
  }

  public StudyEditor(Project project, VirtualFile file) {
    myDefaultEditor = TextEditorProvider.getInstance().createEditor(project, file);
    myComponent = myDefaultEditor.getComponent();
    JPanel studyPanel = new JPanel();
    studyPanel.setLayout(new BoxLayout(studyPanel, BoxLayout.Y_AXIS));
    final JLabel taskText = new JLabel(getTextForTask(file, project));
    taskText.setFont(new Font("Arial", Font.PLAIN, 16));
    HideableTitledPanel taskTextPanel = new HideableTitledPanel("Task Text", taskText, true);
    studyPanel.add(taskTextPanel);
    JPanel studyButtonPanel = new JPanel(new GridLayout(1, 2));
    JPanel taskActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    studyButtonPanel.add(taskActionsPanel);
    studyButtonPanel.add(new JPanel());
    addButton(taskActionsPanel, "Check task", StudyIcons.Resolve);
    addButton(taskActionsPanel, "Prev Task", StudyIcons.Prev);
    addButton(taskActionsPanel, "Next Task", StudyIcons.Next);
    addButton(taskActionsPanel, "Start task again", StudyIcons.Refresh24);
    addButton(taskActionsPanel, "Remind shortcuts", StudyIcons.ShortcutReminder);
    addButton(taskActionsPanel, "Watch test input", StudyIcons.WatchInput);
    addButton(taskActionsPanel, "Run", StudyIcons.Run);

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
