package ru.compscicenter.edide.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyDirectoryProjectGenerator;

import javax.swing.*;

/**
 * author: liana
 * data: 7/31/14.
 */
public class StudyNewProjectDialog extends DialogWrapper{
  private static final String DIALOG_TITLE = "Select The Course";
  JPanel myContentPanel;
  public StudyNewProjectDialog(@Nullable Project project, StudyDirectoryProjectGenerator generator) {
    super(project, true);
    setTitle(DIALOG_TITLE);
    myContentPanel = new StudyNewProjectPanel(project, generator, this).getContentPanel();
    init();

  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public void enableOK(boolean isEnabled) {
    myOKAction.setEnabled(isEnabled);
  }
}
