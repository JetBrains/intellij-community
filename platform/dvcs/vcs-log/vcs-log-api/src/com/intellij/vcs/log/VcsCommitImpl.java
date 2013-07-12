package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Simple implementation of the {@link VcsCommit}: a container for all necessary fields.
 *
 * @author Kirill Likhodedov
 */
public class VcsCommitImpl implements VcsCommit {
  @NotNull private final Hash myHash;
  @NotNull private final List<Hash> myParents;
  @NotNull private final String mySubject;
  @NotNull private final String myAuthorName;
  private final long myAuthorTime;

  public VcsCommitImpl(@NotNull Hash hash, @NotNull List<Hash> parents, @NotNull String subject, @NotNull String author, long time) {
    myHash = hash;
    myParents = parents;
    mySubject = subject;
    myAuthorName = author;
    myAuthorTime = time;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return myHash;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return myParents;
  }

  @NotNull
  @Override
  public String getSubject() {
    return mySubject;
  }

  @NotNull
  @Override
  public String getAuthorName() {
    return myAuthorName;
  }

  @Override
  public long getAuthorTime() {
    return myAuthorTime;
  }
}
