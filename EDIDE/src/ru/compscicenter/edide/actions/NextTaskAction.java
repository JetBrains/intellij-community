package ru.compscicenter.edide.actions;

import ru.compscicenter.edide.editor.StudyEditor;
import ru.compscicenter.edide.course.Task;

import javax.swing.*;

public class NextTaskAction extends TaskNavigationAction {

  @Override
  protected JButton getButton(StudyEditor selectedStudyEditor) {
    return selectedStudyEditor.getNextTaskButton();
  }

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's the last task";
  }

  @Override
  protected Task getTargetTask(Task sourceTask) {
    return sourceTask.next();
  }
}