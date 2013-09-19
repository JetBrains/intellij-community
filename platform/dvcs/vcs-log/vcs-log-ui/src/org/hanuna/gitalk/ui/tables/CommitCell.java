package org.hanuna.gitalk.ui.tables;

import com.intellij.vcs.log.VcsRef;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitCell {

  private final String text;
  private final List<VcsRef> refsToThisCommit;

  public CommitCell(String text, List<VcsRef> refsToThisCommit) {
    this.text = text;
    this.refsToThisCommit = refsToThisCommit;
  }

  public String getText() {
    return text;
  }

  public List<VcsRef> getRefsToThisCommit() {
    return refsToThisCommit;
  }

}
