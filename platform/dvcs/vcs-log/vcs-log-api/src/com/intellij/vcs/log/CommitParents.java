package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitParents {

  @NotNull
  Hash getHash();

  @NotNull
  List<Hash> getParents();

}
