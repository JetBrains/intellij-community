package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcs.log.Hash;
import org.hanuna.gitalk.graph.elements.Node;
import com.intellij.vcs.log.CommitData;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitDataGetter {

  // this method support pre-load beside nodes
  @NotNull
  public CommitData getCommitData(@NotNull Node node) throws VcsException;

  @NotNull
  public CommitData getCommitData(@NotNull Hash commitHash);
}
