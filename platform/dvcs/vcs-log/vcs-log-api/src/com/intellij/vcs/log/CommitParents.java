package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitParents {

  @NotNull
  public Hash getCommitHash();

  @NotNull
  public List<Hash> getParentHashes();

}
