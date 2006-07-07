package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
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
  public abstract void scheduleUpdate(boolean updateUnversionedFiles);
  public abstract boolean ensureUpToDate(boolean canBeCanceled);

  @NotNull
  public abstract List<LocalChangeList> getChangeLists();

  public abstract List<File> getAffectedPaths();
  @NotNull
  public abstract List<VirtualFile> getAffectedFiles();
  public abstract boolean isFileAffected(final VirtualFile file);

  public abstract LocalChangeList addChangeList(@NotNull String name, final String comment);
  public abstract void setDefaultChangeList(@NotNull LocalChangeList list);
  public abstract LocalChangeList getDefaultChangeList();

  public abstract LocalChangeList getChangeList(Change change);

  @Nullable
  public abstract Change getChange(VirtualFile file);

  @NotNull
  public abstract FileStatus getStatus(VirtualFile file);

  @NotNull
  public abstract Collection<Change> getChangesIn(VirtualFile dir);

  @NotNull
  public abstract Collection<Change> getChangesIn(FilePath path);

  public abstract void removeChangeList(final LocalChangeList list);

  public abstract void moveChangesTo(final LocalChangeList list, final Change[] changes);

  public abstract void addChangeListListener(ChangeListListener listener);
  public abstract void removeChangeListListener(ChangeListListener listener);

  public abstract void registerCommitExecutor(CommitExecutor executor);
  
  public abstract void commitChanges(LocalChangeList changeList, List<Change> changes);
  public abstract void reopenFiles(List<FilePath> paths);
}
