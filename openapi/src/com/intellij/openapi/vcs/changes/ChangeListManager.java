package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public abstract class ChangeListManager {
  public static ChangeListManager getInstance(Project project) {
    return project.getComponent(ChangeListManager.class);
  }

  public abstract void scheduleUpdate();
  public abstract boolean ensureUpToDate(boolean canBeCanceled);

  @NotNull
  public abstract List<ChangeList> getChangeLists();

  public abstract List<File> getAffectedPaths();
  @NotNull
  public abstract List<VirtualFile> getAffectedFiles();

  public abstract ChangeList addChangeList(@NotNull String name);
  public abstract void setDefaultChangeList(@NotNull ChangeList list);

  public abstract ChangeList getChangeList(Change change);

  @Nullable
  public abstract Change getChange(VirtualFile file);

  @NotNull
  public abstract Collection<Change> getChangesIn(VirtualFile dir);

  @NotNull
  public abstract Collection<Change> getChangesIn(FilePath path);

  public abstract void removeChangeList(final ChangeList list);

  public abstract void moveChangesTo(final ChangeList list, final Change[] changes);

  public abstract void addChangeListListner(ChangeListListener listener);
  public abstract void removeChangeListListner(ChangeListListener listener);

  public abstract void registerCommitExecutor(CommitExecutor executor);
}
