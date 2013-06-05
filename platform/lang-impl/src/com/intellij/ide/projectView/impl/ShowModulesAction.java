package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.PlatformUtils;

/**
* Created by IntelliJ IDEA.
* User: anna
* Date: 8/5/11
* Time: 9:33 PM
* To change this template use File | Settings | File Templates.
*/
public abstract class ShowModulesAction extends ToggleAction {
  private final Project myProject;

  public ShowModulesAction(Project project) {
    super(IdeBundle.message("action.show.modules"), IdeBundle.message("action.description.show.modules"),
          AllIcons.ObjectBrowser.ShowModules);
    myProject = project;
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return ProjectView.getInstance(myProject).isShowModules(getId());
  }

  protected abstract String getId();

  @Override
  public void setSelected(AnActionEvent event, boolean flag) {
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
    projectView.setShowModules(flag, getId());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
    presentation.setVisible(hasModules() && Comparing.strEqual(projectView.getCurrentViewId(), getId()));
  }

  private static boolean hasModules() {
    return PlatformUtils.isIdea() ||
           PlatformUtils.isCommunity() ||
           PlatformUtils.isFlexIde();
  }
}
