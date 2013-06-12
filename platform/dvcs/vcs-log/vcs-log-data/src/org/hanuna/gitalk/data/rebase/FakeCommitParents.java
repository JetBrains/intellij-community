package org.hanuna.gitalk.data.rebase;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.RebaseCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class FakeCommitParents implements CommitParents {

  private static final String FAKE_HASH_PREFIX = "aaaaaaaaaaaaa00000000";

  public static boolean isFake(Hash hash) {
    return isFake(hash.toStrHash());
  }

  public static boolean isFake(String hashStr) {
    return hashStr.startsWith(FAKE_HASH_PREFIX);
  }

  public static Hash getOriginal(Hash hash) {
    return Hash.build(getOriginal(hash.toStrHash()));
  }

  public static String getOriginal(String hash) {
    while (isFake(hash)) {
      hash = hash.substring(FAKE_HASH_PREFIX.length());
    }
    return hash;
  }

  public static Hash fakeHash(Hash hash) {
    return Hash.build(FAKE_HASH_PREFIX + getOriginal(hash.toStrHash()));
  }

  private final RebaseCommand command;
  private final Hash fakeHash;

  private final Hash parent;

  public FakeCommitParents(@NotNull Hash parent, @NotNull RebaseCommand command) {
    this.parent = parent;
    this.command = command;
    this.fakeHash = fakeHash(command.getCommit());
  }

  @NotNull
  @Override
  public Hash getHash() {
    return fakeHash;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return Collections.singletonList(parent);
  }

  @NotNull
  public RebaseCommand getCommand() {
    return command;
  }

  @Override
  public String toString() {
    return fakeHash + " -> " + command;
  }
}
