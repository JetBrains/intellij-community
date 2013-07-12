package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Full details of a commit: all metadata (commit message, author, committer, etc.) and the changes.
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommitDetails extends VcsCommit {

  @NotNull
  String getFullMessage();

  @NotNull
  String getAuthorEmail();

  @NotNull
  String getCommitterName();

  @NotNull
  String getCommitterEmail();

  long getCommitTime();

  @NotNull
  Collection<Change> getChanges();

}
