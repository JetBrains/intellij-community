package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommit;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitDataGetter {

  // this method support pre-load beside nodes
  @NotNull
  public VcsCommit getCommitData(@NotNull Node node) throws VcsException;

  @NotNull
  public VcsCommit getCommitData(@NotNull Hash commitHash);
}
