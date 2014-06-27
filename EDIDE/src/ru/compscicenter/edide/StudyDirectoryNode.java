package ru.compscicenter.edide;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import javax.swing.*;

/**
 * author: liana
 * data: 6/25/14.
 */
public class StudyDirectoryNode extends PsiDirectoryNode {
  public static final Icon RESOLVED_TASK = AllIcons.General.InformationDialog;
  public static final Icon UNRESOLVED_TASK = AllIcons.General.WarningDialog;
  PsiDirectory myValue;
  Project myProject;
  public StudyDirectoryNode(Project project,
                            PsiDirectory value,
                            ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myValue = value;
    myProject = project;

  }

  @Override
  protected void updateImpl(PresentationData data) {

    //data.setIcon(UNRESOLVED_TASK);
   String pathToIcon = StudyDirectoryNode.class.getResource("unchecked.png").getPath();

    data.setIcon(new ImageIcon(pathToIcon));
    //data.setPresentableText("my" + myValue.getName());
    if (myValue.getName().equals(myProject.getName())) {
      data.setPresentableText(StudyTaskManager.getInstance(myProject).getCourse().getName());
    } else {
      data.setPresentableText(myValue.getName());
    }

  }
}
