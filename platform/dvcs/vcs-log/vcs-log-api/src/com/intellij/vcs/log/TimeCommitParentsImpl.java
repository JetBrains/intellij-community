package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class TimeCommitParentsImpl implements TimeCommitParents {

  @NotNull private final Hash myHash;
  @NotNull private final List<Hash> myParents;
  private final long myTime;

  public TimeCommitParentsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    myHash = hash;
    myParents = parents;
    myTime = timeStamp;
  }

  public long getAuthorTime() {
    return myTime;
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

  @Override
  public String toString() {
    return myTime + "|-" + myHash + "|-" + StringUtil.join(myParents, ",");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TimeCommitParentsImpl parents = (TimeCommitParentsImpl)o;

    if (myTime != parents.myTime) return false;
    if (!myHash.equals(parents.myHash)) return false;
    if (!myParents.equals(parents.myParents)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHash.hashCode();
    result = 31 * result + myParents.hashCode();
    result = 31 * result + (int)(myTime ^ (myTime >>> 32));
    return result;
  }
}
