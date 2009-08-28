package com.intellij.ide.impl;

import com.intellij.ide.CompositeSelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author yole
 */
public class ProjectViewSelectInGroupTarget implements CompositeSelectInTarget, DumbAware {
  @NotNull
  public Collection<SelectInTarget> getSubTargets(SelectInContext context) {
    return ProjectView.getInstance(context.getProject()).getSelectInTargets();
  }

  public boolean canSelect(SelectInContext context) {
    ProjectView projectView = ProjectView.getInstance(context.getProject());
    Collection<SelectInTarget> targets = projectView.getSelectInTargets();
    for (SelectInTarget projectViewTarget : targets) {
      if (projectViewTarget.canSelect(context)) return true;
    }
    return false;
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    ProjectView projectView = ProjectView.getInstance(context.getProject());
    Collection<SelectInTarget> targets = projectView.getSelectInTargets();
    Collection<SelectInTarget> targetsToCheck = new LinkedHashSet<SelectInTarget>();
    String currentId = projectView.getCurrentViewId();
    for (SelectInTarget projectViewTarget : targets) {
      if (Comparing.equal(currentId, projectViewTarget.getMinorViewId())) {
        targetsToCheck.add(projectViewTarget);
        break;
      }
    }
    targetsToCheck.addAll(targets);
    for (final SelectInTarget target : targetsToCheck) {
      if (target.canSelect(context)) {
        if (requestFocus) {
          IdeFocusManager.getInstance(context.getProject()).requestFocus(new FocusCommand() {
            public ActionCallback run() {
              target.selectIn(context, requestFocus);
              return new ActionCallback.Done();
            }
          }, true);
        }
        else {
          target.selectIn(context, requestFocus);
        }
        break;
      }
    }
  }

  public String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return 0;
  }

  @Override
  public String toString() {
    return "Project View";
  }
}
