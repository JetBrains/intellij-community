package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides so called "mini-details" of a commit, that are needed to display information in the log table.
 *
 * @author Kirill Likhodedov
 * @see VcsCommitDetails
 */
public class VcsCommitMiniDetails extends TimeCommitParents {

  @NotNull private final String mySubject;
  @NotNull private final String myAuthorName;

  public VcsCommitMiniDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                              @NotNull String subject, @NotNull String authorName) {
    super(hash, parents, timeStamp);
    mySubject = subject;
    myAuthorName = authorName;
  }

  @NotNull
  public final String getSubject() {
    return mySubject;
  }

  @NotNull
  public final String getAuthorName() {
    return myAuthorName;
  }
}
