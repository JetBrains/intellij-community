package org.hanuna.gitalk.ui.tables.refs.refs;

import com.intellij.vcs.log.Hash;

import java.util.Set;

/**
 * @author erokhins
 */
public interface RefTreeModel {

  public Set<Hash> getCheckedCommits();

  public void inverseSelectCommit(Set<Hash> commits);
}
