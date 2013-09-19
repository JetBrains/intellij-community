package org.hanuna.gitalk.data.rebase;

import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.RebaseCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class FakeCommitParents extends CommitParents {

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

  public FakeCommitParents(@NotNull Hash parent, @NotNull RebaseCommand command) {
    super(fakeHash(command.getCommit()), Collections.singletonList(parent));
    this.command = command;
    this.fakeHash = fakeHash(command.getCommit());
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
