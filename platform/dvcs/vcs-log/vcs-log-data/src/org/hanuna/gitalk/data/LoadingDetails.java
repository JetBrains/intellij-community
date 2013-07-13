package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Fake {@link VcsCommitDetails} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails implements VcsCommitDetails, CommitParents {

  @NotNull private final Hash myHash;

  public LoadingDetails(@NotNull Hash hash) {
    myHash = hash;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return myHash;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getSubject() {
    return "Loading...";
  }

  @NotNull
  @Override
  public String getAuthorName() {
    return "";
  }

  @Override
  public long getAuthorTime() {
    return -1;
  }

  @NotNull
  @Override
  public String getFullMessage() {
    return "";
  }

  @NotNull
  @Override
  public String getAuthorEmail() {
    return "";
  }

  @NotNull
  @Override
  public String getCommitterName() {
    return "";
  }

  @NotNull
  @Override
  public String getCommitterEmail() {
    return "";
  }

  @Override
  public long getCommitTime() {
    return -1;
  }

  @NotNull
  @Override
  public Collection<Change> getChanges() {
    return Collections.emptyList();
  }
}
