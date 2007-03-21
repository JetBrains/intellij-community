package com.intellij.openapi.vcs.changes;

import com.intellij.peer.PeerFactory;
import com.intellij.openapi.project.Project;

import java.util.Collection;

/**
 * @author max
 */
public abstract class LocalChangeList implements Cloneable, ChangeList {
  public static LocalChangeList createEmptyChangeList(Project project, String description) {
    return PeerFactory.getInstance().getVcsContextFactory().createLocalChangeList(project, description);
  }

  public abstract Collection<Change> getChanges();

  public abstract String getName();

  public abstract void setName(String name);

  public abstract String getComment();

  public abstract void setComment(String comment);

  public abstract boolean isDefault();

  public abstract boolean isInUpdate();

  public abstract boolean isReadOnly();

  public abstract void setReadOnly(boolean isReadOnly);

  public abstract LocalChangeList clone();
}
