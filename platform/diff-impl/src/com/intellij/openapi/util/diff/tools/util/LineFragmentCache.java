package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.fragments.LineFragments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LineFragmentCache {
  private final long myModificationStamp1;
  private final long myModificationStamp2;

  @NotNull private final Map<ComparisonPolicy, LineFragments> myFragments;

  public LineFragmentCache(@NotNull LineFragmentCache cache) {
    myModificationStamp1 = cache.myModificationStamp1;
    myModificationStamp2 = cache.myModificationStamp2;

    myFragments = new HashMap<ComparisonPolicy, LineFragments>(3);
    for (Map.Entry<ComparisonPolicy, LineFragments> entry : cache.myFragments.entrySet()) {
      myFragments.put(entry.getKey(), entry.getValue());
    }
  }

  public LineFragmentCache(long modificationStamp1,
                           long modificationStamp2) {
    myModificationStamp1 = modificationStamp1;
    myModificationStamp2 = modificationStamp2;
    myFragments = new HashMap<ComparisonPolicy, LineFragments>(3);
  }

  public long getStamp1() {
    return myModificationStamp1;
  }

  public long getStamp2() {
    return myModificationStamp2;
  }

  public boolean checkStamps(long stamp1, long stamp2) {
    return myModificationStamp1 == stamp1 && myModificationStamp2 == stamp2;
  }

  @Nullable
  public LineFragments getFragments(@NotNull ComparisonPolicy policy) {
    return myFragments.get(policy);
  }

  public void putFragments(@NotNull ComparisonPolicy policy, @NotNull LineFragments fragments) {
    myFragments.put(policy, fragments);
  }
}
