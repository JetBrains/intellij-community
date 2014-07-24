package ru.compscicenter.edide.actions;


import ru.compscicenter.edide.editor.StudyEditor;
import ru.compscicenter.edide.course.Task;

import javax.swing.*;

public class PreviousTaskAction extends TaskNavigationAction {

  @Override
  protected JButton getButton(StudyEditor selectedStudyEditor) {
    return selectedStudyEditor.getPrevTaskButton();
  }

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's already the first task";
  }

  @Override
  protected Task getTargetTask(Task sourceTask) {
    return sourceTask.prev();
  }
}