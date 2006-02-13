package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @author max
 */
public abstract class ChangeListManager {
  public static ChangeListManager getInstance(Project project) {
    return project.getComponent(ChangeListManager.class);
  }

  public abstract void scheduleUpdate();
  public abstract List<ChangeList> getChangeLists();
}
