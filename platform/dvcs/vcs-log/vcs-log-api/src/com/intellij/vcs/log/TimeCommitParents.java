package com.intellij.vcs.log;

/**
 * @author Kirill Likhodedov
 */
public interface TimeCommitParents extends CommitParents {

  long getAuthorTime();

}
